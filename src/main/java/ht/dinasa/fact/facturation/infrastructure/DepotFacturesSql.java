package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.DepotFactures;
import ht.dinasa.fact.facturation.domain.Facture;
import ht.dinasa.fact.facturation.domain.LigneFacture;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Écrit les factures au gallon exactement comme l'ancien système
 * (GenererFacture.aspx.vb + dbo.PS_ENTETE_FACTURE), mais en « tout ou rien »
 * et uniquement pour les factures de cette opération.
 */
@Repository
class DepotFacturesSql implements DepotFactures {

    private static final String INSERER_EN_TETE = """
            INSERT INTO FACT.FACTURE
                   (NO_FACTURE, CODE_TRANSPORTEUR, MONTANT, MONTANT_TRANSACTION, MONTANT_FRAIS_FIXE,
                    MONTANT_SERVICE, SIGLE_MONNAIE, CODE_METHODE_TARIFICATION, DATE_GENERER_FACTURE,
                    POSTE_YN, CREATEDBY, DATECREATED)
            OUTPUT INSERTED.ID_FACTURE
            VALUES (:numero, :transporteur, :montant, :montantTransaction, 0,
                    0, 'USD', 1, :dateGeneration,
                    0, :par, :le)
            """;

    private static final String INSERER_LIGNE = """
            INSERT INTO FACT.DETAIL_FACTURE
                   (NO_FACTURE, CODE_FACTURE, CODE_MOUVEMENT, CODE_TRANSPORTEUR, QTE_COMMANDEE,
                    PRIX_TRANSP_GALON, MONTANT, SIGLE_MONNAIE, CAMION_CODE, DATE_GENERER_FACTURE,
                    CREATEDBY, DATECREATED)
            OUTPUT INSERTED.ID_DETAIL_FACTURE
            VALUES (:numero, :idFacture, :idMouvement, :transporteur, :quantite,
                    :prix, :montant, 'USD', :camion, :dateGeneration,
                    :par, :le)
            """;

    /** Le bon n'est marqué que s'il n'est lié à AUCUNE autre ligne de facture : protection contre la double facturation. */
    private static final String MARQUER_BON = """
            UPDATE dbo.Mouvement
               SET CodeFacture = :idLigne, ModifBy = :par, DateModif = :le
             WHERE ID_Mouvement = :idMouvement
               AND (CodeFacture IS NULL OR CodeFacture = 0)
               AND NOT EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE df
                                WHERE df.CODE_MOUVEMENT = :idMouvement
                                  AND df.ID_DETAIL_FACTURE <> :idLigne)
            """;

    private static final String NUMERO_EXISTE = "SELECT COUNT(*) FROM FACT.FACTURE WHERE NO_FACTURE = :numero";

    private final JdbcClient jdbc;

    DepotFacturesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(List<Facture> factures, String utilisateur) {
        LocalDateTime maintenant = LocalDateTime.now();
        for (Facture facture : factures) {
            verifierNumeroLibre(facture.numero());

            long idFacture = jdbc.sql(INSERER_EN_TETE)
                    .param("numero", facture.numero())
                    .param("transporteur", facture.codeTransporteur())
                    .param("montant", facture.total().valeur())
                    // SQL Server arrondissait cette somme en l'enregistrant : on fait pareil
                    .param("montantTransaction", facture.montantTransactionExact().setScale(2, RoundingMode.HALF_UP))
                    .param("dateGeneration", facture.dateGeneration().toLocalDate())
                    .param("par", utilisateur)
                    .param("le", maintenant)
                    .query(Long.class).single();

            for (LigneFacture ligne : facture.lignes()) {
                long idLigne = jdbc.sql(INSERER_LIGNE)
                        .param("numero", facture.numero())
                        .param("idFacture", idFacture)
                        .param("idMouvement", ligne.idMouvement())
                        .param("transporteur", facture.codeTransporteur())
                        .param("quantite", ligne.quantiteCommandee())
                        .param("prix", ligne.prixAuGallon())
                        .param("montant", ligne.montant().valeur())
                        .param("camion", ligne.codeCamion())
                        .param("dateGeneration", facture.dateGeneration().toLocalDate())
                        .param("par", utilisateur)
                        .param("le", maintenant)
                        .query(Long.class).single();

                int marques = jdbc.sql(MARQUER_BON)
                        .param("idLigne", idLigne)
                        .param("idMouvement", ligne.idMouvement())
                        .param("par", utilisateur)
                        .param("le", maintenant)
                        .update();
                if (marques != 1) {
                    // l'exception annule TOUTE l'opération (transaction)
                    throw new ErreurMetier("BON_DEJA_FACTURE",
                            "Le bon " + ligne.numeroBon() + " (n° " + ligne.idMouvement()
                                    + ") a déjà été facturé entre-temps. Aucune facture n'a été enregistrée.");
                }
            }
        }
    }

    private void verifierNumeroLibre(String numero) {
        Long existe = jdbc.sql(NUMERO_EXISTE).param("numero", numero).query(Long.class).single();
        if (existe > 0) {
            throw new ErreurMetier("NUMERO_FACTURE_EXISTANT",
                    "Le numéro de facture " + numero + " existe déjà. Relancez la génération dans une seconde.");
        }
    }
}