# Enregistrement des demandes d'execution

Cette etape prepare le stockage du suivi. Elle n'execute aucun agent et ne connecte pas encore LangGraph.

## API

- POST `/api/missions/{missionId}/execute` : aucun corps requis. Cree une execution PENDING et une ligne PENDING par etape enregistree. Reponse 202 avec l'identifiant et un en-tete Location vers sa consultation.
- GET `/api/executions/{executionId}` : lit l'execution globale.
- GET `/api/executions/{executionId}/steps` : lit les etapes dans l'ordre de leur creation.
- GET `/api/missions/{missionId}/executions` : consulte l'historique de cette mission.

`requestedAt` correspond a la demande. `startedAt` et `finishedAt` restent vides tant qu'aucun moteur ne prend en charge l'execution. Aucune sortie ni reussite n'est inventee.

## Organisation du code

1. MissionExecutionService.create verifie les references agent/version, les instructions et les limites courantes, puis utilise les repositories pour enregistrer les deux niveaux de suivi.
2. MissionRepository.findByIdForUpdate verrouille la mission pendant la transaction. Deux demandes simultanees ne peuvent pas creer deux executions actives pour cette mission. La sauvegarde du workflow et la suppression de mission utilisent le meme verrou.
3. MissionExecutionRepository.existsByMissionIdAndStatusIn detecte une execution PENDING ou RUNNING. Le controleur renvoie alors 409 avec un message.
4. MissionExecutionController expose le POST et la consultation des etapes. Les GET globaux existants restent compatibles.
5. Frontend/src/api/missionApi.js fournit requestExecution, getExecution et getExecutionSteps. Ces fonctions ne sont pas encore appelees par le bouton de simulation.

## Limites de cette etape

Le bouton Run actuel reste une simulation locale. Une demande creee par cette API reste PENDING jusqu'au branchement du moteur ; elle n'est pas automatiquement distribuee. Comme le service conserve deja les workflows ayant un historique, leur modification et leur suppression sont refusees apres creation d'une execution : dupliquer la mission pour continuer a la modifier.

La validation complete du graphe, la confirmation des versions aupres de M1, la gestion globale de la concurrence, l'annulation, les delais, les reprises et la progression seront completes avec le moteur. La validation realisee ici est un premier controle avant enregistrement.

## Verification

ExecutionRegistrationTest utilise exclusivement une base H2 temporaire : persistance des deux niveaux, erreurs 400/404/409, refus d'une limite abaissee et lancement concurrent. Aucun essai n'ajoute d'execution dans la base MySQL personnelle.

Redemarrer Spring Boot pour charger les endpoints et ajouter la colonne requested_at avec la configuration de developpement ddl-auto=update. Pour essayer dans Swagger, utiliser une mission de test avec son workflow enregistre.


## Conditions If/Else exécutées par LangGraph

Les workflows contenant une condition sont transmis dans un ordre topologique avec les dépendances de chaque étape. LangGraph choisit la branche Oui (`yes`) ou Non (`no`), propage `SKIPPED` dans la branche inactive et exécute les étapes communes dès que leurs prédécesseurs ont été traités. Les cycles restent refusés. Les workflows sans condition gardent leur plan séquentiel ou parallèle actuel. Dans cette première version, les étapes d'un plan conditionnel sont traitées dans l'ordre topologique, sans parallélisme interne au plan conditionnel.

- `M1_MODE=mock` : la condition utilise uniquement `conditionSampleValue`, explicitement identifié comme valeur de test. La trace indique `simulated: true`.
- `M1_MODE=real` : la condition lit uniquement un nombre fini dans le JSON de sortie d'un agent déjà exécuté. `score` désigne le champ du dernier agent exécuté ; `step-42.score` désigne le champ de l'étape sauvegardée 42. Les chemins imbriqués, par exemple `evaluation.score`, sont acceptés.
- Aucun remplacement par la valeur de test en mode réel. JSON absent, invalide, champ absent, null, booléen ou texte provoquent une erreur explicite.
- Opérateurs autorisés : `=`, `!=`, `>`, `>=`, `<`, `<=`. Aucune expression n'est exécutée avec `eval`.
- Chaque condition nécessite exactement une connexion Oui et une connexion Non. Les étapes ignorées ne consomment ni tentative ni token.
- Le résultat reste candidat tant qu'il n'a pas été publié côté serveur. Cette modification n'ajoute aucune décision M6 et aucune boucle de correction.

Exemple : agent A -> condition `score >= 70` -> Oui : agent B / Non : agent C -> agent D commun. En mode mock, une valeur de test 80 exécute A, B et D ; C est `SKIPPED`. Une valeur 20 exécute A, C et D ; B est `SKIPPED`.
