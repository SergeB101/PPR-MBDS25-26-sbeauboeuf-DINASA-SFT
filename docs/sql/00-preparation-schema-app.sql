00-preparation-schema-app.sqlUSE [DINASA_FACTURATION_DB_bk];
GO
CREATE SCHEMA APP AUTHORIZATION dbo;
GO
-- L'application peut créer des tables, mais seulement là où elle a le droit ALTER : le schéma APP
GRANT CREATE TABLE TO dinasa_app;
GRANT ALTER, SELECT, INSERT, UPDATE, DELETE, REFERENCES ON SCHEMA::APP TO dinasa_app;
GO

-- à lancer après le premier démarrage
DENY UPDATE, DELETE ON APP.JOURNAL_AUDIT TO dinasa_app;