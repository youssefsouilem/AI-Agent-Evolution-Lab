
import os
import hmac
from typing import Literal
from uuid import uuid4
from workflow import execution_lock

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, model_validator

from workflow import (
    start_mission,
    resume_mission,
    get_mission_state
)

app = FastAPI(
    title="AI Agent Evolution Lab - M3 Orchestrator",
    version="2.0.0"
)


@app.middleware("http")
async def protect_orchestrator(request: Request, call_next):
    token = os.getenv("ORCHESTRATOR_TOKEN", "")
    if token and request.url.path.startswith("/api/") and not hmac.compare_digest(request.headers.get("Authorization", ""), "Bearer " + token):
        return JSONResponse(status_code=401, content={"detail": "Authentification du service requise"})
    return await call_next(request)


class Parent(BaseModel):
    id: str
    branch: Literal["yes", "no"] | None = None


class Correction(BaseModel):
    target: str
    maxAttempts: int = Field(ge=1, le=10)
    steps: list[str] = Field(min_length=2, max_length=100)


class Condition(BaseModel):
    variable: str | None = None
    operator: Literal["=", "!=", ">", ">=", "<", "<=", "is_true", "is_false"]
    expected: float = Field(allow_inf_nan=False)
    sample: float | None = Field(default=None, allow_inf_nan=False)
    correction: Correction | None = None


class Stage(BaseModel):
    id: str | None = None
    kind: Literal["agent", "parallel", "approval", "condition", "control"] = "agent"
    agentId: str | None = Field(default=None, pattern=r"^[1-9]\d*$")
    agentVersionId: str | None = Field(default=None, pattern=r"^[1-9]\d*$")
    instruction: str | None = None
    inputMapping: str | None = None
    expectedOutput: str | None = None
    agents: list[dict] = Field(default_factory=list)
    parents: list[Parent] | None = None
    condition: Condition | None = None
    retry_to: int | None = None
    max_attempts: int = Field(default=2, ge=1, le=10)
    message: str | None = None

    @model_validator(mode="after")
    def validate_stage(self):
        if self.kind == "agent" and (not self.agentId or not self.agentVersionId):
            raise ValueError("agentId et agentVersionId obligatoires")

        if self.kind == "condition" and self.condition is None:
            raise ValueError("Configuration de condition obligatoire")
        if self.kind in ("condition", "control") and self.parents is None:
            raise ValueError("Dépendances du workflow obligatoires")
        if self.kind == "parallel":
            if not self.agents:
                raise ValueError("agents ne doit pas être vide")
            if len(self.agents) > 100 or len({agent.get("id") for agent in self.agents}) != len(self.agents):
                raise ValueError("Groupe parallèle trop grand ou identifiants dupliqués")
            for agent in self.agents:
                if not agent.get("id") or not str(agent.get("agentId", "")).isdigit() or not str(agent.get("agentVersionId", "")).isdigit():
                    raise ValueError(
                        "Chaque worker nécessite id, agentId et agentVersionId"
                    )
        return self


class MissionRequest(BaseModel):
    mission_id: int = Field(gt=0)
    execution_id: str = Field(default_factory=lambda: str(uuid4()))
    instruction: str = Field(min_length=1)
    stages: list[Stage] = Field(min_length=1, max_length=100)
    max_duration: int = Field(default=300, ge=1, le=3600)
    max_step_duration: int = Field(default=30, ge=1, le=600)
    max_parallel: int = Field(default=4, ge=1, le=32)

    @model_validator(mode="after")
    def validate_workflow(self):
        ids = set()
        for i, stage in enumerate(self.stages):
            stage.id = stage.id or f"stage-{i}"
            if stage.id in ids:
                raise ValueError("Identifiants d'étape dupliqués")
            ids.add(stage.id)
            if stage.retry_to is not None:
                if stage.kind != "agent":
                    raise ValueError(
                        "retry_to est réservé aux stages agent"
                    )
                if not 0 <= stage.retry_to <= i:
                    raise ValueError(
                        "retry_to doit pointer vers une étape "
                        "précédente ou courante"
                    )
        # Conditional workflows are a topologically ordered DAG, never an executable expression.
        if any(stage.parents is not None for stage in self.stages):
            seen = {}
            for stage in self.stages:
                if stage.parents is None or stage.kind not in ("agent", "condition", "control") or stage.retry_to is not None:
                    raise ValueError("Le mode conditionnel nécessite un DAG sans boucle")
                for parent in stage.parents:
                    if parent.id not in seen:
                        raise ValueError("Dépendance absente ou non topologique")
                    if (seen[parent.id].kind == "condition") != (parent.branch is not None):
                        raise ValueError("Branche conditionnelle absente ou inattendue")
                seen[stage.id] = stage
            roots = [stage for stage in self.stages if not stage.parents]
            if len(roots) != 1:
                raise ValueError("Un seul début de workflow est requis")
            for stage in self.stages:
                if stage.kind == "condition":
                    branches = [parent.branch for child in self.stages for parent in child.parents if parent.id == stage.id]
                    if stage.condition.correction is not None:
                        branches.append("no")
                    if sorted(branches) != ["no", "yes"]:
                        raise ValueError("Une condition nécessite exactement Oui et Non")
            used = set()
            for stage in self.stages:
                correction = stage.condition.correction if stage.kind == "condition" else None
                if correction is None:
                    continue
                body = correction.steps
                if body[0] != correction.target or body[-1] != stage.id or len(set(body)) != len(body) or used.intersection(body):
                    raise ValueError("Segment de correction invalide ou chevauchant")
                for position, key in enumerate(body[:-1]):
                    if key not in seen or seen[key].kind != "agent" or correction.maxAttempts > seen[key].max_attempts:
                        raise ValueError("Retour de correction non borné par les limites des agents")
                    children = [child.id for child in self.stages if any(parent.id == key for parent in child.parents)]
                    if children != [body[position + 1]]:
                        raise ValueError("Le segment de correction doit être une chaîne d'agents")
                for key in body[1:]:
                    if len(seen[key].parents) != 1:
                        raise ValueError("Le segment de correction ne peut pas avoir d'entrée intermédiaire")
                used.update(body)
        return self


class ResumeRequest(BaseModel):
    approved: bool


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "langgraph-orchestrator"
    }


@app.post("/api/orchestrator/execute")
def execute(request: MissionRequest):
    if not execution_lock.acquire(blocking=False):
        raise HTTPException(status_code=409, detail="Le moteur traite déjà une demande ; réessayez ensuite.")
    try:
        existing = get_mission_state(request.execution_id)

        if existing is not None:
            raise HTTPException(
                status_code=409,
                detail="execution_id déjà utilisé"
            )

        result = start_mission(
            mission_id=request.mission_id,
            instruction=request.instruction,
            stages=[
                stage.model_dump(exclude_none=True)
                for stage in request.stages
            ],
            execution_id=request.execution_id,
            max_duration=request.max_duration,
            max_step_duration=request.max_step_duration,
            max_parallel=request.max_parallel
        )

        snapshot = get_mission_state(request.execution_id)

        return {
            "execution_id": request.execution_id,
            **snapshot
        }

    except HTTPException:
        raise
    except Exception as error:
        raise HTTPException(
            status_code=500,
            detail=str(error)
        )


    finally:
        execution_lock.release()


@app.get("/api/orchestrator/executions/{execution_id}")
def get_execution(execution_id: str):
    result = get_mission_state(execution_id)

    if result is None:
        raise HTTPException(
            status_code=404,
            detail="Exécution introuvable"
        )

    return {
        "execution_id": execution_id,
        **result
    }


@app.post("/api/orchestrator/executions/{execution_id}/resume")
def resume(execution_id: str, request: ResumeRequest):
    if not execution_lock.acquire(blocking=False):
        raise HTTPException(status_code=409, detail="Une autre demande est en cours.")
    try:
        snapshot = get_mission_state(execution_id)

        if snapshot is None:
            raise HTTPException(
                status_code=404,
                detail="Exécution introuvable"
            )

        if snapshot["status"] != "PAUSED":
            raise HTTPException(
                status_code=409,
                detail="Cette exécution n'est pas en pause"
            )

        try:
            resume_mission(
                execution_id,
                request.approved
            )

            return {
                "execution_id": execution_id,
                **get_mission_state(execution_id)
            }
        except Exception as error:
            raise HTTPException(
                status_code=500,
                detail=str(error)
            )

    finally:
        execution_lock.release()


class GenerateRequest(BaseModel):
    mission_id: int = Field(gt=0)
    instruction: str = Field(min_length=1, max_length=4000)
    router_agent_id: str = Field(pattern=r"^[1-9]\d*$")
    router_version_id: str = Field(pattern=r"^[1-9]\d*$")
    catalogue: list[dict] = Field(min_length=1, max_length=500)
    max_steps: int = Field(ge=1, le=100)


class GeneratedAgent(BaseModel):
    name: str = Field(min_length=1, max_length=160)
    agentId: str = Field(pattern=r"^[1-9]\d*$")
    agentVersionId: str = Field(pattern=r"^[1-9]\d*$")
    instruction: str = Field(min_length=1, max_length=4000)


class GeneratedPlan(BaseModel):
    agents: list[GeneratedAgent] = Field(min_length=1, max_length=100)


@app.post("/api/orchestrator/generate")
def generate(request: GenerateRequest):
    import json
    import workflow
    if workflow.M1_MODE != "real":
        raise HTTPException(status_code=503, detail="La génération IA nécessite M1_MODE=real et un AI Router M1 configuré")
    if not execution_lock.acquire(blocking=False):
        raise HTTPException(status_code=409, detail="Le moteur traite déjà une demande")
    try:
        prompt = ("Propose un workflow séquentiel pour cet objectif. Choisis uniquement les identifiants et versions du catalogue. "
            "Réponds uniquement avec un objet JSON de la forme {\"agents\":[{\"name\":\"Étape\",\"agentId\":\"1\",\"agentVersionId\":\"11\",\"instruction\":\"Tâche\"}]}. "
            f"Maximum {request.max_steps} étapes. Objectif : " + request.instruction + " Catalogue : " + json.dumps(request.catalogue, ensure_ascii=False))
        output = workflow.execute_agent(request.router_agent_id, prompt,
            {"executionId": "generation-" + str(uuid4()), "callId": "router", "outputs": {}, "maxStepDuration": 60}, request.router_version_id)
        if output.get("status") != "SUCCESS":
            raise ValueError("AI Router indisponible ou réponse invalide")
        plan = GeneratedPlan.model_validate(json.loads(output["output"]))
        if len(plan.agents) > request.max_steps:
            raise ValueError("Le plan dépasse la limite d'étapes")
        allowed = {(str(agent["id"]), str(version)) for agent in request.catalogue for version in agent["versions"]}
        if any((agent.agentId, agent.agentVersionId) not in allowed for agent in plan.agents):
            raise ValueError("Le plan référence un agent ou une version hors catalogue")
        return {"steps": [{**agent.model_dump(), "stepOrder": index, "nodeType": "AGENT"} for index, agent in enumerate(plan.agents)],
                "edges": [{"sourceStepOrder": index, "targetStepOrder": index + 1} for index in range(len(plan.agents) - 1)]}
    except (ValueError, KeyError, TypeError) as error:
        raise HTTPException(status_code=502, detail="Plan AI Router invalide : " + str(error)) from None
    finally:
        execution_lock.release()
