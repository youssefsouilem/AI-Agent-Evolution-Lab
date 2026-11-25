# Liaison M3 vers M6

## Responsabilites

M3 enverra les mesures issues de l'execution. M6 calcule le diagnostic d'anomalie et retourne sa gravite, son motif et son indicateur de blocage. M3 conserve la decision metier et le suivi de la mission. Aucun calcul Isolation Forest ou de score M6 n'est duplique dans M3.

Le contrat est prepare d'apres les pages 39 a 43 de la presentation, mais doit etre confirme avec le vrai M6. La presentation comporte aussi un exemple avec `/api/anomalies/check` : le chemin retenu ici est `/api/anomalies/detect`, configurable.

## Tester M3 seul dans Swagger

POST `http://localhost:8083/api/monitoring/check`

```json
{
  "executionId": 31,
  "durationMs": 2400,
  "tokensUsed": 850,
  "attempts": 2,
  "status": "COMPLETED"
}
```

Le statut decrit le resultat candidat de l'execution avant la decision de publication. Valeurs acceptees : COMPLETED ou FAILED. Les valeurs numeriques sont obligatoires et non negatives ; executionId doit etre positif.

Avec `M6_ENABLED=false`, la reponse est une fixture et contient toujours :

```json
{
  "executionId": 31,
  "source": "SIMULATION",
  "simulated": true,
  "anomalyDetected": false,
  "severity": "NORMAL",
  "reason": "SIMULATION_NORMAL",
  "blocking": false
}
```

Cette fixture ne calcule rien a partir des metriques envoyees. Choisir `M6_MOCK_SCENARIO=NORMAL`, MINOR ou CRITICAL dans les variables d'environnement IntelliJ, puis redemarrer pour tester chaque cas. Les exemples sont dans `src/main/resources/mocks/m6-diagnostics.json`. Le scenario CRITICAL retourne blocking=true.

Cette API de verification n'exige pas qu'executionId existe dans MySQL et ne modifie aucune mission : elle sert a tester le transport et le contrat. Ne pas utiliser les valeurs saisies par un client comme mesures de reference du moteur.

## Activer le vrai M6

Variables d'environnement dans la configuration Run IntelliJ :

```text
M6_ENABLED=true
M6_BASE_URL=http://localhost:8086
M6_DETECT_PATH=/api/anomalies/detect
M6_CONNECT_TIMEOUT_MS=2000
M6_READ_TIMEOUT_MS=5000
```

Le port 8086 est un exemple, a remplacer par le vrai port. Si M6 tourne sur un autre PC, localhost ne designe pas ce PC : utiliser son adresse accessible.

M6MonitoringClient envoie un POST avec les cinq champs de MonitoringMetricsRequest. Il attend les champs anomalyDetected (booleen), severity (NORMAL, MINOR ou CRITICAL), reason (texte optionnel) et blocking (booleen). Les noms, la pagination eventuelle, les valeurs de gravite et l'authentification doivent etre compares au contrat reel ; aucune conversion silencieuse de LOW ou HIGH n'est effectuee.

Une reponse reelle contient source=M6 et simulated=false. Une reponse incomplete ou incompatible donne HTTP 502. Une panne ou un timeout donne HTTP 503, sans diagnostic invente et sans repli silencieux vers la simulation. Aucun retry POST automatique n'est effectue.

## Raccordements restants

- Le moteur fournira automatiquement les vrais tokens, les durees et les tentatives a MonitoringService.check avant la publication du resultat.
- M3 devra appliquer blocking=true, conserver le diagnostic et gerer une indisponibilite selon la politique convenue. Une gravite MINOR n'est pas, a elle seule, une validation de qualite.
- Le formulaire Run n'est pas modifie dans cette etape. `Frontend/src/services/monitoringApi.js` propose checkExecutionMetrics pour les essais ; le diagnostic final doit etre demande et applique par le backend.
- La securisation des appels entre services sera raccordee au Keycloak commun. Aucun jeton n'est configure ici.

## Verification automatisee

M6MonitoringClientTest teste un vrai echange HTTP avec un serveur local de test : contenu des metriques, decision bloquante, chemin configurable, panne, reponse incomplete et timeout.
MonitoringApiTest verifie les trois fixtures, la validation HTTP 400 et les erreurs 502/503. Ces tests n'appellent pas le vrai M6 et ne modifient pas MySQL.
