-- Journal d'audit : une ligne par opération sensible, jamais modifiée ni supprimée.
CREATE TABLE APP.JOURNAL_AUDIT (
                                   ID_JOURNAL      bigint IDENTITY(1, 1) NOT NULL CONSTRAINT PK_JOURNAL_AUDIT PRIMARY KEY,
                                   DATE_OPERATION  datetime2(3)  NOT NULL CONSTRAINT DF_JOURNAL_AUDIT_DATE DEFAULT (SYSDATETIME()),
                                   UTILISATEUR     nvarchar(50)  NOT NULL,
                                   ACTION          nvarchar(60)  NOT NULL,   -- ex. FACTURE_LIGNE_CORRIGEE
                                   OBJET_TYPE      nvarchar(60)  NOT NULL,   -- ex. DETAIL_FACTURE
                                   OBJET_ID        nvarchar(60)  NOT NULL,
                                   ID_FACTURE      bigint        NULL,       -- pour retrouver l'historique d'une facture
                                   MOTIF           nvarchar(500) NULL,
                                   AVANT           nvarchar(max) NULL,       -- valeurs avant, au format JSON
                                   APRES           nvarchar(max) NULL        -- valeurs après, au format JSON
);

CREATE INDEX IX_JOURNAL_AUDIT_FACTURE ON APP.JOURNAL_AUDIT (ID_FACTURE);
CREATE INDEX IX_JOURNAL_AUDIT_OBJET   ON APP.JOURNAL_AUDIT (OBJET_TYPE, OBJET_ID);
CREATE INDEX IX_JOURNAL_AUDIT_DATE    ON APP.JOURNAL_AUDIT (DATE_OPERATION);