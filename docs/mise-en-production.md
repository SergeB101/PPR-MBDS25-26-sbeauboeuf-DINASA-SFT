# Mise en production — dinasa-fact-api

## 1. Base de données (sur le serveur SQL, par un administrateur)
- [ ] Sauvegarde complète de la base de facturation AVANT tout
- [ ] Créer le login `dinasa_app` avec un mot de passe fort (différent du poste de développement)
- [ ] Droits : db_datareader, db_datawriter, EXECUTE, CREATE TABLE
- [ ] Lancer `docs/sql/00-preparation-schema-app.sql` (schéma APP + droits sur APP uniquement)
- [ ] Après le premier démarrage (tables APP créées) :
  DENY UPDATE, DELETE ON APP.JOURNAL_AUDIT TO dinasa_app;
  DENY UPDATE, DELETE ON APP.JOURNAL_CONNEXION TO dinasa_app;

## 2. Variables d'environnement du serveur d'application
| Variable | Exemple / règle |
|---|---|
| SPRING_PROFILES_ACTIVE | prod |
| DB_URL | jdbc:sqlserver://VS-W16-SQL2:1433;databaseName=...;encrypt=true;trustServerCertificate=true |
| DB_USER / DB_PASSWORD | dinasa_app / (coffre-fort, jamais dans Git) |
| JWT_SECRET | 64 caractères aléatoires, propres à ce serveur |
| ORIGINES_AUTORISEES | https://adresse-de-l-ecran-react |
| DINASA_INIT_LOGIN / DINASA_INIT_MDP | premier démarrage SEULEMENT, puis supprimer |

## 3. Démarrage
- [ ] `java -jar dinasa-fact-api.jar` (le profil vient de SPRING_PROFILES_ACTIVE)
- [ ] Accès uniquement en HTTPS, derrière IIS ou nginx
- [ ] Vérifier : /actuator/health = UP ; /swagger-ui.html = introuvable
- [ ] Premier administrateur : connexion, changement de mot de passe, puis retirer DINASA_INIT_*
- [ ] Donner les droits aux groupes (validés avec le métier)

## 4. Pendant la période où les deux applications tournent
- [ ] La récupération ACCPAC n'est lancée QUE depuis une des deux applications
- [ ] Le posting n'est fait QUE depuis une des deux applications