# Présentation locale de M3

Cette procédure montre les fonctionnalités propres à M3 avec des agents simulés. Elle ne constitue pas une démonstration d'analyse par un vrai LLM.

## 1. MySQL

Démarrer MySQL sur le port 3306. Sur le PC de développement, `application-local.properties` contient la configuration locale ignorée par Git. Sur un autre PC, fournir `DB_URL`, `DB_USERNAME` et `DB_PASSWORD`. Aucun secret ne doit être copié dans le README ou le dépôt partagé.

Le schéma est géré par JPA. Pour une ancienne base MySQL, le démarrage élargit les colonnes d'entrées et de résultats de TEXT vers LONGTEXT si nécessaire ; il ne supprime aucune donnée. Utiliser un compte autorisé à mettre à jour le schéma.

## 2. Moteur Python

Ouvrir un terminal dans `Backend/ai-services/langgraph-orchestrator` :

```powershell
$env:M1_MODE='mock'
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8093
```

Installation sur un autre PC : voir le README de ce dossier.

## 3. Backend Java

Dans IntelliJ, lancer `MissionApplication` avec le profil actif `demo` (ou la variable `SPRING_PROFILES_ACTIVE=demo`). Si Maven est disponible dans le terminal, depuis `m3-mission-service` :

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Le profil demo désactive la découverte Eureka, Config Client, les appels M1/M6, les événements et l'authentification JWT. Il conserve la connexion au moteur Python. Ce profil est réservé à une démonstration locale.

Vérifier `http://localhost:8083/actuator/health` : statut `UP`. Swagger : `http://localhost:8083/swagger-ui.html`.

## 4. Frontend

Dans le dossier Frontend :

```powershell
npm install
$env:VITE_API_URL='http://localhost:8083'
npm run dev
```

Ouvrir `http://localhost:5173`. Utiliser bien `localhost` pour correspondre à l'origine CORS autorisée.

## Parcours à présenter

1. Créer une mission manuelle ou partir d'un modèle prêt.
2. Ajouter les agents, leurs instructions et leurs connexions. Enregistrer.
3. Lancer Run et montrer les étapes, les tentatives et les sorties simulées.
4. Recharger la page pour montrer la sauvegarde serveur.
5. Pour If/Else, configurer les sorties Oui et Non et changer la valeur de test.
6. Pour une correction : CodeFixer → Reviewer → condition. Relier Oui à la sortie et Non à CodeFixer ; la connexion indique « correction limitée ». Mettre la limite de CodeFixer et Reviewer à 2. Pour un booléen `valid`, utiliser `is_true`, valeur de test 0 pour montrer l'arrêt à la limite ou 1 pour valider immédiatement.
7. La limite est aussi bornée par la mission et l'administrateur. Le premier passage est la tentative 1. Montrer l'historique dans Run.

Après une exécution, le workflow est verrouillé pour conserver l'historique : dupliquer la mission pour en modifier les étapes.

## Ce qui exige les autres services

- Une vraie analyse et la génération libre par AI Router exigent M1 et un LLM réellement configurés. En leur absence, choisir le mode manuel ou un modèle prêt.
- La publication d'un résultat réel exige un contrôle M6 réel non bloquant. Le résultat mock reste un candidat non publié. L'action de reprise pilotée par M6 n'est pas définie dans le contrat actuel.
- Le login Keycloak/M2, l'inscription réelle Eureka, le vrai Config Server et la consommation RabbitMQ par M7 restent à tester avec l'équipe.
- Docker : fichiers préparés et contrôlés statiquement, mais construction/exécution non testées faute de moteur Docker disponible.

Le changement de rôle dans la barre latérale est un mode de démonstration ; ce n'est pas un login sécurisé.
