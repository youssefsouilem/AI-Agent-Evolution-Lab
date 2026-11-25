# M3 Mission Service (IntelliJ IDEA)

## Ouvrir
1. IntelliJ IDEA > Open > choisir `Backend/m3-mission-service` (dossier contenant `pom.xml`).
2. Attendre l'import Maven ; selectionner JDK 17 ou 21 (target Java 17).
3. Demarrer MySQL sur le port 3306. La base `m3_mission_db` est creee automatiquement si l'utilisateur MySQL en a le droit.
4. Configurer `DB_USER` et `DB_PASSWORD` dans la configuration Run d'IntelliJ (ou utiliser le compte root sans mot de passe uniquement en local).
5. Lancer `MissionApplication.java`.
6. Ouvrir http://localhost:8083/swagger-ui/index.html

## Maven
`mvn clean compile`
`mvn spring-boot:run`

## Exemples Swagger
POST /api/missions :
```json
{"name":"Recherche IA","objective":"Produire une synthese","selectionMode":"MANUAL","maxAttempts":3,"ownerId":"demo"}
```
PUT /api/missions/1/workflow :
```json
{
  "steps":[
    {"name":"Debut","stepOrder":0,"nodeType":"START"},
    {"name":"Recherche","stepOrder":1,"nodeType":"AGENT","agentId":"mock-researcher","instruction":"Faire une recherche"},
    {"name":"Fin","stepOrder":2,"nodeType":"END"}
  ],
  "edges":[
    {"sourceStepOrder":0,"targetStepOrder":1},
    {"sourceStepOrder":1,"targetStepOrder":2}
  ]
}
```
`agentId` est une simple reference a M1 : aucune execution d'agent n'est implementee.

## Perimetre
- Fonctionnels : CRUD missions, duplication des missions et workflows, stockage des etapes et connexions, lecture des executions, validations, erreur 404/400, Swagger.
- Non implementes : execution et creation d'executions, M1, LangGraph, M6, M7, RabbitMQ, AI Router, authentification.
- Les autres microservices sont des dossiers vides pour l'integration d'equipe.
- `ddl-auto=update` est reserve au developpement ; utiliser des migrations pour une production.

## Parametres d'orchestration (API backend)

- `GET /api/orchestration-settings` : lire la configuration globale ; tant qu'aucune sauvegarde n'existe, renvoie les valeurs par defaut sans creer de ligne.
- `PUT /api/orchestration-settings` : enregistrer les six champs ci-dessous. Les mises a jour reutilisent la ligne d'identifiant 1 dans `orchestration_settings`.

```json
{
  "maxRetries": 4,
  "maxDuration": 300,
  "maxStepDuration": 30,
  "maxSteps": 30,
  "maxConcurrent": 2,
  "allowConditions": true
}
```

`maxRetries` designe ici le nombre maximal de tentatives. Bornes : 1..10 tentatives, 10..3600 secondes par mission, 1..600 secondes par etape, 1..100 etapes, 1..10 executions simultanees. Tous les champs sont obligatoires. Les valeurs invalides renvoient HTTP 400 et ne remplacent pas la configuration precedente.

Redemarrer Spring Boot pour charger le nouveau controleur et laisser Hibernate creer la table avec le `ddl-auto=update` de developpement. Tester les deux operations dans Swagger. La persistance et la validation sont couvertes par `OrchestrationSettingsApiTest` sur H2, sans modifier MySQL.

Le formulaire React charge et enregistre maintenant les reglages via cette API. Une copie locale sert a l'affichage ; les parametres sont recharges avant la sauvegarde et le lancement d'une simulation, ainsi qu'au retour sur une fenetre utilisant le hook. WorkflowService refuse les etapes AGENT dont maxRetries depasse la limite serveur, avant toute modification du workflow. Les anciennes etapes au-dessus de la nouvelle limite doivent etre corrigees explicitement. L'application des autres limites cote serveur, le moteur d'execution et la restriction du PUT au role administrateur restent a integrer.


## Eureka Client : préparation à l'intégration

M3 utilise Spring Cloud 2025.0.3 avec Spring Boot 3.5.7 et s'enregistre sous `m3-mission-service` sur le port `8083`. L'annotation `@EnableDiscoveryClient` rend son rôle explicite. Aucun serveur Eureka n'est créé dans ce microservice.

Par défaut, `EUREKA_ENABLED=false` : le backend fonctionne sans serveur de découverte.
Dans IntelliJ, ajouter ces variables à la configuration de lancement pour rejoindre le serveur de l'équipe :

```text
EUREKA_ENABLED=true
EUREKA_SERVER_URL=http://localhost:8761/eureka/
```

Si Eureka tourne sur un autre PC, remplacer `localhost` par son adresse réseau. Dans Docker, `localhost` désigne le conteneur lui-même : utiliser `host.docker.internal` pour un serveur lancé sur l'hôte, ou le nom/adresse du serveur dans le réseau commun. Le fichier Compose transmet les deux variables à M3.

Après redémarrage de M3, ouvrir le tableau de bord `http://ADRESSE_EUREKA:8761/` et vérifier `M3-MISSION-SERVICE` avec l'état `UP`. L'adresse IP et le port annoncés doivent être accessibles aux autres services. Si l'IP détectée ne convient pas, la propriété standard `eureka.instance.ip-address` peut être fournie dans la configuration de lancement ; ne pas annoncer `127.0.0.1` aux autres PC.

Cette configuration permet l'inscription et la lecture de l'annuaire. Les clients HTTP M1/M6 actuels gardent leurs URL configurées : ils ne deviennent pas automatiquement des clients avec résolution par nom Eureka. Le moteur Python n'est pas inscrit par cette modification.

Références : [compatibilité Spring Cloud](https://spring.io/projects/spring-cloud/) et [Eureka Client](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html).


## Config Client : préparation au serveur commun

La dépendance `spring-cloud-starter-config` est gérée par le BOM Spring Cloud existant. Aucun Config Server n'est créé dans M3 : le serveur et son dépôt Git sont communs à l'équipe.

Le client est désactivé par défaut (`CONFIG_CLIENT_ENABLED=false`). Les propriétés locales et l'import facultatif `application-local.properties` sont conservés. Pour joindre le Config Server depuis IntelliJ, ajouter à la configuration de lancement :

```text
CONFIG_CLIENT_ENABLED=true
CONFIG_SERVER_URL=http://localhost:8888
CONFIG_PROFILE=default
CONFIG_LABEL=main
```

Le port 8888 et la branche `main` sont des valeurs proposées, à confirmer avec l'équipe. Pour un serveur sur un autre PC, remplacer `localhost` par son IP réseau. Dans Docker, utiliser `host.docker.internal` pour un serveur sur l'hôte ou l'adresse du serveur dans le réseau commun. Les quatre variables sont transmises par Compose.

La requête de configuration attendue est `/m3-mission-service/default/main`. Le fichier `Infrastructure/config-repository/m3-mission-service.properties` est un exemple à copier dans le dépôt Git réellement servi par le Config Server. Sa présence dans ce projet ne crée ni serveur ni dépôt distant automatiquement. Les secrets restent dans les variables d'environnement ou le coffre-fort de l'équipe.

L'import `optional:configserver:` autorise le démarrage avec les propriétés locales si le serveur est indisponible. Pour un environnement exigeant la configuration distante, utiliser `SPRING_CONFIG_IMPORT=configserver:http://ADRESSE:8888` (sans `optional:`), avec le client activé. La configuration distante est lue au démarrage ; ces ajouts ne mettent pas en place de rafraîchissement dynamique ni Spring Cloud Bus.

Référence : [Spring Cloud Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html).

## Docker : démarrage local de M3

Les Dockerfile Java, Python et frontend existent. `Infrastructure/docker-compose.yml` réunit MySQL, LangGraph, M3 et React ; RabbitMQ est un profil optionnel `events`. La base MySQL du conteneur est séparée de la base personnelle locale.

1. Installer et démarrer Docker Desktop si nécessaire.
2. Dans le dossier `Infrastructure`, utiliser le `.env` local existant. S'il n'existe pas, copier `.env.example` vers `.env` puis renseigner les mots de passe, le token interne et le chemin `FRONTEND_CONTEXT` vers le frontend. Ne pas écraser un `.env` déjà configuré.
3. Vérifier et démarrer :

```powershell
docker compose config --quiet
docker compose up --build -d
```

Interface : `http://localhost:5173`. API : `http://localhost:8083`. Santé : `http://localhost:8083/actuator/health`. Le frontend Docker passe par le proxy Nginx `/backend/` vers M3. La base conteneurisée est accessible sur le port local 3307, sans remplacer le port MySQL local 3306.

Pour observer les services : `docker compose ps`, puis `docker compose logs -f m3 orchestrator`. Pour les arrêter sans supprimer les données : `docker compose down` (sans `-v`). La suppression des volumes effacerait les données conteneurisées et les checkpoints.

Eureka et Config Client restent désactivés par défaut dans Compose. Les activer dans `.env` lorsque les serveurs communs sont prêts. Les appels réels M1/M6, le login M2 et les bindings M7 doivent être testés avec leurs responsables.

Vérification du présent changement : validation statique du YAML, des variables requises locales et de l'existence des Dockerfile/chemins de construction. Docker n'étant pas installé dans l'environnement utilisé, aucune construction ni exécution des conteneurs n'est certifiée par cette validation statique.
