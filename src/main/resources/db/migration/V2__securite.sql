-- Comptes de la nouvelle application : 1 ligne par utilisateur de dbo.tbl_S_User (ID_UTILISATEUR = tbl_S_User.ID)
CREATE TABLE APP.COMPTE_SECURITE (
                                     ID_UTILISATEUR     bigint        NOT NULL CONSTRAINT PK_COMPTE_SECURITE PRIMARY KEY,
                                     MOT_DE_PASSE       nvarchar(200) NOT NULL,              -- empreinte BCrypt, jamais le mot de passe
                                     DOIT_CHANGER       bit           NOT NULL CONSTRAINT DF_COMPTE_DOIT_CHANGER DEFAULT (1),
                                     ECHECS             int           NOT NULL CONSTRAINT DF_COMPTE_ECHECS DEFAULT (0),
                                     BLOQUE_JUSQU_A     datetime2(0)  NULL,
                                     VERSION_JETON      int           NOT NULL CONSTRAINT DF_COMPTE_VERSION DEFAULT (0), -- +1 = anciens jetons invalides
                                     DERNIERE_CONNEXION datetime2(0)  NULL,
                                     DERNIERE_IP        nvarchar(45)  NULL,
                                     MODIFIE_LE         datetime2(0)  NOT NULL,
                                     MODIFIE_PAR        nvarchar(50)  NOT NULL
);

-- Droits accordés à un groupe de dbo.tbl_S_Group
CREATE TABLE APP.GROUPE_PERMISSION (
                                       GROUP_ID    bigint       NOT NULL,
                                       PERMISSION  nvarchar(60) NOT NULL,
                                       ACCORDE_LE  datetime2(0) NOT NULL,
                                       ACCORDE_PAR nvarchar(50) NOT NULL,
                                       CONSTRAINT PK_GROUPE_PERMISSION PRIMARY KEY (GROUP_ID, PERMISSION)
);

CREATE TABLE APP.JOURNAL_CONNEXION (
                                       ID_CONNEXION   bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_JOURNAL_CONNEXION PRIMARY KEY,
                                       DATE_CONNEXION datetime2(0) NOT NULL,
                                       LOGIN          nvarchar(50) NOT NULL,
                                       RESULTAT       nvarchar(40) NOT NULL,
                                       IP             nvarchar(45) NULL
);
CREATE INDEX IX_JOURNAL_CONNEXION_LOGIN ON APP.JOURNAL_CONNEXION (LOGIN, DATE_CONNEXION);