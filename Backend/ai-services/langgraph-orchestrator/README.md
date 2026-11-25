# Moteur LangGraph de M3

Ce service FastAPI exécute le workflow demandé par Spring Boot : étapes séquentielles, groupes parallèles, conditions et retours de correction explicitement bornés. Les checkpoints et le registre des appels sont conservés dans SQLite.

## Installation

Utiliser Python 3.12 avec SQLite. L'environnement local `.venv` est installé sur le PC de développement mais n'est pas à versionner. Sur un autre PC, depuis ce dossier :

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.lock.txt
```

Si `py` n'est pas disponible, sélectionner explicitement l'exécutable Python 3.12 installé sur ce PC.

## Démonstration autonome

```powershell
$env:M1_MODE='mock'
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8093
```

Vérifier `http://localhost:8093/health`. Le mode mock ne produit aucune analyse IA réelle et utilise la valeur de test des conditions. Une correction compte son premier passage comme tentative 1.

Les données SQLite restent dans le dossier courant par défaut. `CHECKPOINT_DB` et `CALLS_DB` permettent d'utiliser un autre emplacement. Garder les checkpoints avec la base M3 correspondante ; ne pas mélanger les identifiants d'exécution de bases différentes.

## Appels réels M1

Renseigner `M1_MODE=real`, `M1_BASE_URL` et, si nécessaire, `M1_BEARER_TOKEN`. Le contrat attendu est `POST /api/agents/{id}/execute` avec `versionId`, `input` et `context`. La réponse doit contenir `status=SUCCESS`, la version demandée et un `output` non vide. Les métriques absentes restent inconnues.

Les conditions réelles lisent le JSON de `output`. Exemple Reviewer : `{"valid":false,"feedback":"Corriger la syntaxe"}` avec l'opérateur `is_true`. Aucune valeur de test ne remplace un champ absent en mode réel.

`ORCHESTRATOR_TOKEN` protège les routes `/api/` avec un token de service ; la même valeur doit être fournie à Spring Boot. Ne pas exposer directement le moteur Python sur Internet.

La génération `/api/orchestrator/generate` exige un AI Router M1 réel configuré côté Java. Elle ne bascule pas silencieusement sur une génération fictive.

## Tests

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Les tests utilisent des bases temporaires et des services simulés. Ils ne nécessitent pas de clé LLM ni les services des autres membres.
