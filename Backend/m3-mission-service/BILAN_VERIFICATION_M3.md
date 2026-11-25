# Bilan de vérification M3 — 24 novembre 2026

## Résultat
La démonstration locale démarre avec MySQL, Spring Boot, React et le moteur Python. Le profil `demo` utilise le catalogue et les appels agents simulés ; il ne démontre pas une analyse produite par un vrai LLM.

- Frontend : http://localhost:5173
- Santé Spring Boot : http://localhost:8083/actuator/health
- Swagger : http://localhost:8083/swagger-ui/index.html
- Moteur Python : http://127.0.0.1:8093/health
- Instructions de démarrage : DEMONSTRATION.md.

## Vérifications exécutées
- Java : Maven verify réussi, 47 tests, aucun échec ni erreur ; JAR exécutable produit.
- Python : 26 tests réussis dans le nouvel environnement .venv du projet.
- Frontend : 20 tests réussis et compilation de production réussie.
- HTTP avec une base MySQL de test séparée : CRUD, sauvegarde/rechargement des workflows, exécutions séquentielles et parallèles, conditions Oui/Non, branches SKIPPED, étapes communes, corrections limitées et historique, refus des cycles accidentels et limites administrateur.
- Persistance : entrée de plus de 70 Ko et états d’exécution enregistrés puis relus avec MySQL.
- Navigateur Chrome automatisé : création depuis un modèle, création manuelle, sauvegarde puis Run sans rechargement, rechargement du résultat, navigation et séparation des menus utilisateur/administrateur. Aucun crash JavaScript dans les parcours testés.
- Contrats M1/M6 : appels HTTP testés avec des services de test locaux, notamment versions, résultats JSON, erreurs, génération et décisions de publication. Ces tests ne remplacent pas l’intégration avec les services de l’équipe.
- Dernière vérification sur la base personnelle : lecture des pages principales et santé des services, sans créer de mission de test dans cette base.

## Corrections principales
1. Réponses de surveillance incomplètes ou incohérentes : publication refusée et résultat conservé en attente.
2. Santé RabbitMQ désactivée lorsque les événements sont désactivés, pour éviter un faux statut DOWN.
3. Identifiants des étapes synchronisés après sauvegarde : timeline mise à jour immédiatement après Run.
4. Erreurs de surveillance conservées correctement pendant le polling React.
5. Valeurs de test des conditions booléennes normalisées.
6. Colonnes de gros contenus en LONGTEXT et migration ciblée des anciennes colonnes MySQL ; aucune suppression de données.
7. Connexions SQLite correctement fermées ; résultats simulés évitant une croissance récursive excessive.
8. Libellés de l’interface corrigés pour refléter les fonctions effectivement disponibles.

## Fichiers ajoutés pendant cet audit
- config/MySqlLargeTextMigration.java : élargissement idempotent des colonnes MySQL concernées.
- application-demo.properties : démarrage local sans dépendre des services de l’équipe.
- Tests Java : PublicationServiceTest, SecurityApiTest, MissionEventPublisherTest, WorkflowGenerationServiceTest et MySqlLargeTextMigrationTest.
- README.md du moteur Python : installation et configuration mock/réelle.
- DEMONSTRATION.md et ce bilan : démarrage, parcours et limites.
- .venv du moteur Python : environnement local et dépendances installées, non destiné à Git.

## Limites à annoncer
Les vrais services M1, M6, M7, Keycloak, Eureka et Config Server de l’équipe n’étaient pas disponibles : leur intégration complète n’est pas certifiée. Docker n’est pas installé sur ce poste : les images et le démarrage Compose n’ont pas été exécutés.

La génération libre d’un workflow par IA nécessite un agent AI Router et M1 configurés. Les modèles de mission restent utilisables pour la démonstration locale. Le profil demo désactive la sécurité et les intégrations externes : il est réservé à une démonstration locale.

Les versions et le chat restent stockés dans le navigateur. Le catalogue de modèles est prédéfini. La reprise pilotée par une décision réelle de M6 reste à valider avec son contrat. Les tests exécutés couvrent les parcours décrits ; ils ne constituent pas une garantie de fonctionnement de tout scénario possible.
