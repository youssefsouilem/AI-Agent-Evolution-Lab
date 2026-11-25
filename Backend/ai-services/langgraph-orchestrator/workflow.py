
import os
import sqlite3
import operator
import json
import hashlib
import threading
from contextlib import closing
import time
import math
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated, TypedDict, Literal

import httpx
from langgraph.graph import StateGraph, START, END
from langgraph.types import Command, Send, interrupt
from langgraph.checkpoint.sqlite import SqliteSaver


# ==========================================
# CONFIGURATION M1
# ==========================================

M1_MODE = os.getenv("M1_MODE", "mock")
M1_BASE_URL = os.getenv("M1_BASE_URL", "http://localhost:8081").rstrip("/")
M1_EXECUTE_URL = os.getenv("M1_EXECUTE_URL", "")
MAX_PARALLEL = int(os.getenv("MAX_PARALLEL", "4"))
if not 1 <= MAX_PARALLEL <= 32:
    raise ValueError("MAX_PARALLEL doit être compris entre 1 et 32")
agent_slots = threading.BoundedSemaphore(MAX_PARALLEL)
execution_lock = threading.Lock()
CHECKPOINT_DB = os.getenv("CHECKPOINT_DB", "langgraph_checkpoints.sqlite")
CALLS_DB = os.getenv("CALLS_DB", CHECKPOINT_DB + ".calls.sqlite")


def execute_agent(agent_id, instruction, context, version_id):
    # A stable key is reused if LangGraph replays this exact step attempt.
    identity = json.dumps([context["executionId"], context["callId"]])
    key = hashlib.sha256(identity.encode()).hexdigest()
    with closing(sqlite3.connect(CALLS_DB, timeout=30)) as db, db:
        db.execute("CREATE TABLE IF NOT EXISTS calls (key TEXT PRIMARY KEY, result TEXT)")
        inserted = db.execute("INSERT OR IGNORE INTO calls(key) VALUES (?)", (key,)).rowcount
        if not inserted:
            row = db.execute("SELECT result FROM calls WHERE key = ?", (key,)).fetchone()
            if row[0] is None:
                return {"status": "FAILED", "error": "Appel précédent de résultat inconnu : vérification manuelle nécessaire."}
            return json.loads(row[0])
    call_started = time.time()
    started_at = datetime.now(timezone.utc).isoformat()
    try:
        with agent_slots:
            remaining = context.get("deadline", time.time() + 300) - time.time()
            if remaining <= 0:
                raise TimeoutError("Durée maximale de mission dépassée")
            timeout_seconds = min(context.get("maxStepDuration", 30), remaining)
            if M1_MODE == "mock":
                result = {
                    "agentId": agent_id, "versionId": version_id,
                    "status": "SUCCESS", "simulated": True,
                    "output": f"[MOCK {agent_id}] {instruction}\nEntrées : " + json.dumps(list(context.get("outputs", {})), ensure_ascii=False),
                    "valid": True, "tokensUsed": 0, "durationMs": 0
                }
            elif M1_MODE == "real":
                url = (M1_EXECUTE_URL.format(agent_id=agent_id) if M1_EXECUTE_URL
                       else f"{M1_BASE_URL}/api/agents/{agent_id}/execute")
                headers = {"Idempotency-Key": key}
                if os.getenv("M1_BEARER_TOKEN"):
                    headers["Authorization"] = "Bearer " + os.environ["M1_BEARER_TOKEN"]
                response = httpx.post(url, json={
                    "versionId": int(version_id),
                    "input": instruction + "\n\nObjectif de mission : " + context.get("instruction", "") + "\nEntrée propre à l'étape : " + (context.get("inputMapping") or "") + "\nSortie attendue : " + (context.get("expectedOutput") or "") + "\n\nRésultats précédents :\n" + json.dumps(context.get("outputs", {}), ensure_ascii=False),
                    "context": context
                }, headers=headers, timeout=httpx.Timeout(timeout_seconds, connect=min(3, timeout_seconds)))
                response.raise_for_status()
                result = response.json()
                if result.get("status") != "SUCCESS" or not isinstance(result.get("output"), str) or not result["output"].strip():
                    raise ValueError("Réponse M1 : statut ou output invalide")
                if result.get("versionId") != int(version_id):
                    raise ValueError("M1 n'a pas exécuté la version demandée")
                result["simulated"] = False
                # A reviewer must return structured JSON in output, not an invented valid=True.
                try:
                    review = json.loads(result["output"])
                    if isinstance(review, dict) and isinstance(review.get("valid"), bool):
                        result["valid"] = review["valid"]
                except ValueError:
                    pass
            else:
                raise ValueError("M1_MODE doit être mock ou real")
    except Exception as error:
        result = {"status": "FAILED", "error": f"Échec d'appel agent : {type(error).__name__}", "simulated": M1_MODE == "mock"}
    if time.time() > context.get("deadline", float("inf")) or time.time() - call_started > context.get("maxStepDuration", float("inf")):
        result = {"status": "FAILED", "error": "Durée maximale dépassée ; résultat non diffusé.", "simulated": M1_MODE == "mock"}
    result["startedAt"] = started_at
    result["finishedAt"] = datetime.now(timezone.utc).isoformat()
    result["wallDurationMs"] = round((time.time() - call_started) * 1000)
    with closing(sqlite3.connect(CALLS_DB, timeout=30)) as db, db:
        db.execute("UPDATE calls SET result = ? WHERE key = ?", (json.dumps(result), key))
    return result


# ==========================================
# STATE LANGGRAPH
# ==========================================

class MissionState(TypedDict, total=False):
    mission_id: int
    execution_id: str
    simulated: bool
    started_at: str
    finished_at: str
    deadline: float
    paused_at: float
    max_step_duration: int
    max_parallel: int
    executed_steps: list[str]
    skipped_steps: list[str]
    decisions: dict
    control_results: dict
    condition_history: list[dict]
    parallel_batch: str
    instruction: str
    stages: list[dict]
    current_index: int
    attempts: dict
    outputs: dict
    results: Annotated[list[dict], operator.add]
    status: str
    error: str

    # Données privées des workers Send
    task: dict


def condition_value(condition, state):
    if state["simulated"]:
        value = condition.get("sample")
    else:
        path = (condition.get("variable") or "").split(".")
        outputs = state.get("outputs", {})
        if path[0].startswith("step-"):
            source = outputs.get(path.pop(0)[5:])
            if source is None:
                raise ValueError("Agent source absent des résultats")
        else:
            source = next((outputs[key] for key in reversed(state.get("executed_steps", [])) if key in outputs), None)
        if source is None:
            raise ValueError("Aucun résultat agent disponible")
        try:
            value = json.loads(source["output"])
            for key in path:
                value = value[key]
        except (ValueError, KeyError, TypeError):
            raise ValueError("Variable JSON absente ou résultat non structuré") from None
    if condition.get("operator") in ("is_true", "is_false"):
        if state["simulated"] and type(value) in (int, float) and value in (0, 1):
            value = bool(value)
        if not isinstance(value, bool):
            raise ValueError("La condition nécessite un booléen JSON true ou false")
        return value
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError("La condition nécessite une valeur numérique finie")
    return value


def run_stage(state: MissionState):
    stages = state["stages"]
    index = state["current_index"]

    if index >= len(stages):
        return Command(
            update={"status": "COMPLETED", "finished_at": datetime.now(timezone.utc).isoformat()},
            goto=END
        )

    stage = stages[index]
    step_id = stage.get("id", str(index))
    if time.time() > state["deadline"]:
        return Command(update={"status": "FAILED", "error": "Durée maximale de mission dépassée", "finished_at": datetime.now(timezone.utc).isoformat()}, goto=END)
    if stage.get("parents") is not None:
        parents = stage["parents"]
        executed = state.get("executed_steps", [])
        if step_id in executed:
            return Command(update={"current_index": index + 1}, goto="run")
        active = not parents or any(parent["id"] in executed and
            (parent.get("branch") is None or state.get("decisions", {}).get(parent["id"]) == parent["branch"])
            for parent in parents)
        if not active:
            return Command(update={"skipped_steps": state.get("skipped_steps", []) + [step_id], "current_index": index + 1}, goto="run")
        if stage["kind"] in ("condition", "control"):
            controls = dict(state.get("control_results", {}))
            decisions = dict(state.get("decisions", {}))
            if stage["kind"] == "condition":
                try:
                    actual = condition_value(stage["condition"], state)
                    expected = stage["condition"]["expected"]
                    comparisons = {"=": operator.eq, "!=": operator.ne, ">": operator.gt, ">=": operator.ge, "<": operator.lt, "<=": operator.le, "is_true": lambda value, _: value is True, "is_false": lambda value, _: value is False}
                    branch = "yes" if comparisons[stage["condition"]["operator"]](actual, expected) else "no"
                    decisions[step_id] = branch
                    controls[step_id] = {"actual": actual, "operator": stage["condition"]["operator"], "expected": expected, "branch": branch, "simulated": state["simulated"]}
                    correction = stage["condition"].get("correction")
                    visit = len([entry for entry in state.get("condition_history", []) if entry["stepId"] == step_id]) + 1
                    history = state.get("condition_history", []) + [{"stepId": step_id, "visit": visit, **controls[step_id]}]
                    if correction and branch == "no":
                        used = state.get("attempts", {}).get(correction["target"], 0)
                        if used >= correction["maxAttempts"]:
                            message = f"Limite de correction atteinte : {used}/{correction['maxAttempts']} tentatives."
                            controls[step_id]["error"] = message
                            return Command(update={"status": "FAILED", "error": message, "control_results": controls, "condition_history": history, "finished_at": datetime.now(timezone.utc).isoformat()}, goto=END)
                        body = set(correction["steps"])
                        target_index = next(i for i, item in enumerate(stages) if item["id"] == correction["target"])
                        return Command(update={"current_index": target_index,
                            "executed_steps": [key for key in executed if key not in body],
                            "skipped_steps": [key for key in state.get("skipped_steps", []) if key not in body],
                            "decisions": {key: value for key, value in decisions.items() if key not in body},
                            "control_results": {key: value for key, value in controls.items() if key not in body},
                            "condition_history": history}, goto="run")
                except (ValueError, KeyError, TypeError) as error:
                    controls[step_id] = {"error": str(error)}
                    return Command(update={"status": "FAILED", "error": f"Condition {step_id} : {error}", "control_results": controls, "finished_at": datetime.now(timezone.utc).isoformat()}, goto=END)
            else:
                controls[step_id] = {"status": "COMPLETED"}
            return Command(update={"current_index": index + 1, "executed_steps": executed + [step_id], "decisions": decisions, "control_results": controls,
                "condition_history": history if stage["kind"] == "condition" else state.get("condition_history", [])}, goto="run")

    # --------------------------------------
    # PAUSE / VALIDATION HUMAINE
    # --------------------------------------
    if stage.get("kind") == "approval":
        return Command(update={"status": "PAUSED", "paused_at": time.time()}, goto="approval")

    # --------------------------------------
    # EXÉCUTION PARALLÈLE
    # --------------------------------------
    if stage.get("kind") == "parallel":
        visits = dict(state.get("attempts", {}))
        batch_key = f"parallel-{index}"
        visits[batch_key] = visits.get(batch_key, 0) + 1
        return Command(
            update={"status": "RUNNING", "attempts": visits, "parallel_batch": f"{index}:{visits[batch_key]}"},
            goto="dispatch"
        )

    # --------------------------------------
    # EXÉCUTION D'UN AGENT
    # --------------------------------------
    agent_id = stage["agentId"]
    step_id = stage.get("id", str(index))

    attempts = dict(state.get("attempts", {}))
    attempts[step_id] = attempts.get(step_id, 0) + 1

    result = execute_agent(
        agent_id=agent_id,
        instruction=stage.get(
            "instruction", state["instruction"]
        ),
        context={
            "missionId": state["mission_id"],
            "executionId": state["execution_id"],
            "callId": f"{index}:{step_id}:{attempts[step_id]}",
            "outputs": state.get("outputs", {}),
            "instruction": state["instruction"],
            "inputMapping": stage.get("inputMapping"),
            "expectedOutput": stage.get("expectedOutput"),
            "deadline": state["deadline"],
            "maxStepDuration": state["max_step_duration"]
        },
        version_id=stage["agentVersionId"]
    )

    outputs = dict(state.get("outputs", {}))
    outputs[step_id] = result

    update = {
        "executed_steps": state.get("executed_steps", []) + ([step_id] if result.get("status") == "SUCCESS" else []),
        "attempts": attempts,
        "outputs": outputs,
        "results": [{
            "stepId": step_id,
            "agentId": agent_id,
            "attempt": attempts[step_id],
            "output": result
        }],
        "status": "RUNNING"
    }

    if result.get("status") != "SUCCESS":
        return Command(update={**update, "status": "FAILED", "error": result.get("error", "Échec agent")}, goto=END)

    # --------------------------------------
    # CONDITION ET BOUCLE DE CORRECTION
    # --------------------------------------
    if "retry_to" in stage:
        # M1 doit retourner un champ valid.
        # Une réponse sans valid n'est pas validée.
        if not isinstance(result.get("valid"), bool):
            return Command(update={**update, "status": "FAILED", "error": "Le Reviewer doit renvoyer un JSON contenant valid: true ou false."}, goto=END)
        valid = result["valid"]

        if not valid:
            max_attempts = stage.get("max_attempts", 2)

            if attempts[step_id] < max_attempts:
                return Command(
                    update=update,
                    goto="run",
                    # On revient à l'étape indiquée
                ) if stage["retry_to"] == index else Command(
                    update={
                        **update,
                        "current_index": stage["retry_to"]
                    },
                    goto="run"
                )

            return Command(
                update={
                    **update,
                    "status": "FAILED",
                    "error": "Nombre maximal de tentatives atteint"
                },
                goto=END
            )

    return Command(
        update={
            **update,
            "current_index": index + 1
        },
        goto="run"
    )


# ==========================================
# SEND : PARALLÉLISME DYNAMIQUE
# ==========================================

def dispatch_workers(state: MissionState):
    stage = state["stages"][state["current_index"]]

    return [
        Send("worker", {
            "mission_id": state["mission_id"],
            "execution_id": state["execution_id"],
            "parallel_batch": state["parallel_batch"],
            "deadline": state["deadline"],
            "max_step_duration": state["max_step_duration"],
            "instruction": state["instruction"],
            "outputs": state.get("outputs", {}),
            "task": agent
        })
        for agent in stage["agents"]
    ]


def worker(state: MissionState):
    task = state["task"]

    result = execute_agent(
        task["agentId"],
        task.get("instruction", state["instruction"]),
        {
            "missionId": state["mission_id"],
            "executionId": state["execution_id"],
            "callId": f"{state['parallel_batch']}:{task['id']}",
            "outputs": state.get("outputs", {}),
            "instruction": state["instruction"],
            "inputMapping": task.get("inputMapping"),
            "expectedOutput": task.get("expectedOutput"),
            "deadline": state["deadline"],
            "maxStepDuration": state["max_step_duration"]
        },
        version_id=task["agentVersionId"]
    )

    return {
        "results": [{
            "batch": state["parallel_batch"],
            "stepId": task["id"],
            "agentId": task["agentId"],
            "output": result
        }]
    }


def join_workers(state: MissionState):
    outputs = dict(state.get("outputs", {}))

    # Résultats accumulés grâce au reducer
    batch = {item["stepId"]: item for item in state["results"] if item.get("batch") == state["parallel_batch"]}
    expected = {task["id"] for task in state["stages"][state["current_index"]]["agents"]}
    if set(batch) != expected or any(item["output"].get("status") != "SUCCESS" for item in batch.values()):
        return Command(update={"status": "FAILED", "error": "Échec partiel du groupe parallèle ; synthèse non exécutée."}, goto=END)
    for item in batch.values():
        outputs[item["stepId"]] = item["output"]

    return Command(
        update={
            "outputs": outputs,
            "current_index": state["current_index"] + 1
        },
        goto="run"
    )


# ==========================================
# CONSTRUCTION DU GRAPHE
# ==========================================

def approval_node(state: MissionState):
    stage = state["stages"][state["current_index"]]
    approved = interrupt({"mission_id": state["mission_id"], "message": stage.get("message", "Approuver cette étape ?")})
    if approved is not True:
        return Command(update={"status": "FAILED", "error": "Validation refusée", "finished_at": datetime.now(timezone.utc).isoformat()}, goto=END)
    return Command(update={"current_index": state["current_index"] + 1, "status": "RUNNING",
        "deadline": state["deadline"] + max(0, time.time() - state.get("paused_at", time.time()))}, goto="run")


builder = StateGraph(MissionState)

builder.add_node("run", run_stage)
builder.add_node("approval", approval_node)
builder.add_node("dispatch", lambda state: {})
builder.add_node("worker", worker)
builder.add_node("join", join_workers)

builder.add_edge(START, "run")

builder.add_conditional_edges(
    "dispatch",
    dispatch_workers,
    ["worker"]
)

builder.add_edge("worker", "join")

# Persistance locale pour développement.
connection = sqlite3.connect(
    CHECKPOINT_DB,
    check_same_thread=False
)

checkpointer = SqliteSaver(connection)

graph = builder.compile(
    checkpointer=checkpointer
)


def start_mission(mission_id, instruction, stages, execution_id, max_duration=300, max_step_duration=30, max_parallel=4):
    initial_state = {
        "mission_id": mission_id,
        "execution_id": str(execution_id),
        "simulated": M1_MODE == "mock",
        "started_at": datetime.now(timezone.utc).isoformat(),
        "deadline": time.time() + max_duration,
        "max_step_duration": max_step_duration,
        "max_parallel": min(MAX_PARALLEL, max_parallel),
        "instruction": instruction,
        "stages": stages,
        "current_index": 0,
        "attempts": {},
        "executed_steps": [], "skipped_steps": [], "decisions": {}, "control_results": {}, "condition_history": [],
        "outputs": {},
        "results": [],
        "status": "RUNNING",
        "error": ""
    }

    config = {
        "configurable": {
            "thread_id": execution_id
        },
        "recursion_limit": 20000, "max_concurrency": min(MAX_PARALLEL, max_parallel)
    }

    return graph.invoke(initial_state, config)


def resume_mission(execution_id, approved):
    config = {
        "configurable": {
            "thread_id": execution_id
        },
        "recursion_limit": 20000, "max_concurrency": graph.get_state({"configurable": {"thread_id": execution_id}}).values.get("max_parallel", MAX_PARALLEL)
    }

    return graph.invoke(
        Command(resume=approved),
        config
    )


def get_mission_state(execution_id):
    config = {
        "configurable": {
            "thread_id": execution_id
        }
    }

    snapshot = graph.get_state(config)

    if not snapshot.values:
        return None

    return {
        "status": (
            "PAUSED"
            if any(task.interrupts for task in snapshot.tasks)
            else snapshot.values.get("status", "UNKNOWN")
        ),
        "simulated": snapshot.values.get("simulated", M1_MODE == "mock"),
        "pending_nodes": list(snapshot.next),
        "state": snapshot.values
    }
