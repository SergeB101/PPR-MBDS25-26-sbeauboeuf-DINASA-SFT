package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.DepotServices;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
class DepotServicesSql implements DepotServices {

    /** Le premier bon de transport sert de rattachement aux lignes de service (« artifice » de l'ancien système). */
    private static final String EN_TETE = """
            SELECT f.ID_FACTURE, f.NO_FACTURE, f.CODE_TRANSPORTEUR, f.SIGLE_MONNAIE,
                   f.DATE_GENERER_FACTURE, f.POSTE_YN,
                   (SELECT MIN(d.CODE_MOUVEMENT) FROM FACT.DETAIL_FACTURE d
                     WHERE d.CODE_FACTURE = f.ID_FACTURE AND d.PRIX_SERVICE IS NULL) AS PREMIER_BON
              FROM FACT.FACTURE f WITH (UPDLOCK)
             WHERE f.ID_FACTURE = :id
            """;

    private static final String TYPES = """
            SELECT ID_TYPE_SERVICE_A_DEDUIRE, LIBELLE, ISNULL(SIGNE, 0) AS SIGNE
              FROM FACT.REF_TYPE_SERVICE_A_DEDUIRE
            """;

    private static final String EN_ATTENTE = """
            SELECT ID_SERVICE_A_DEDUIRE_FACTURE, CODE_TYPE_SERVICE_A_DEDUIRE, MONTANT, DATE_SERVICE, NOTE_SERVICE
              FROM FACT.SERVICE_A_DEDUIRE_FACTURE WITH (UPDLOCK)
             WHERE CODE_TRANSPORTEUR = :transporteur
               AND FACTURE_YN = 0
               AND DATE_SERVICE >= :debut AND DATE_SERVICE < :fin
               AND (SIGLE_MONNAIE = :devise OR SIGLE_MONNAIE IS NULL)
             ORDER BY DATE_SERVICE, ID_SERVICE_A_DEDUIRE_FACTURE
            """;

    private static final String INSERER_LIGNE = """
            INSERT INTO FACT.DETAIL_FACTURE
                   (NO_FACTURE, CODE_FACTURE, CODE_MOUVEMENT, CODE_TRANSPORTEUR, JOURS_DISPONIBILITE,
                    MONTANT, PRIX_SERVICE, CODE_TYPE_SERVICE_A_DEDUIRE, DESCRIPTION_LIGNE, SIGLE_MONNAIE,
                    DATE_GENERER_FACTURE, CAMION_CODE, CREATEDBY, DATECREATED)
            OUTPUT INSERTED.ID_DETAIL_FACTURE
            VALUES (:numero, :idFacture, :bon, :transporteur, 0,
                    :montant, :montant, :type, :description, :devise,
                    :dateGeneration, '', :par, :le)
            """;

    private static final String MAJ_EN_TETE = """
            UPDATE FACT.FACTURE
               SET MONTANT_SERVICE = ISNULL(MONTANT_SERVICE, 0) + :montant,
                   MONTANT = MONTANT + :montant,
                   MODIFBY = :par, DATEMODIF = :le
             WHERE ID_FACTURE = :id AND POSTE_YN = 0
            """;

    private static final String INSERER_SERVICE = """
            INSERT INTO FACT.SERVICE_A_DEDUIRE_FACTURE
                   (CODE_TRANSPORTEUR, CODE_TYPE_SERVICE_A_DEDUIRE, DATE_SERVICE, MONTANT, SIGLE_MONNAIE,
                    CODE_FACTURE, NOTE_SERVICE, FACTURE_YN, CREATEDBY, DATECREATED)
            VALUES (:transporteur, :type, :date, :montant, :devise,
                    :idLigne, :note, 1, :par, :le)
            """;

    private static final String MARQUER_SERVICE = """
            UPDATE FACT.SERVICE_A_DEDUIRE_FACTURE
               SET CODE_FACTURE = :idLigne, FACTURE_YN = 1, MODIFBY = :par, DATEMODIF = :le
             WHERE ID_SERVICE_A_DEDUIRE_FACTURE = :id AND FACTURE_YN = 0
            """;

    private final JdbcClient jdbc;

    DepotServicesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EnTeteFacture> enTetePourModification(long idFacture) {
        return jdbc.sql(EN_TETE).param("id", idFacture)
                .query((rs, n) -> {
                    long bon = rs.getLong("PREMIER_BON");
                    Long premierBon = rs.wasNull() ? null : bon;
                    return new EnTeteFacture(
                            rs.getLong("ID_FACTURE"), rs.getString("NO_FACTURE"), rs.getLong("CODE_TRANSPORTEUR"),
                            rs.getString("SIGLE_MONNAIE"), rs.getObject("DATE_GENERER_FACTURE", LocalDate.class),
                            rs.getBoolean("POSTE_YN"), premierBon);
                })
                .optional();
    }

    @Override
    public List<TypeService> typesServices() {
        return jdbc.sql(TYPES + " ORDER BY LIBELLE").query(this::versType).list();
    }

    @Override
    public Optional<TypeService> typeService(int idType) {
        return jdbc.sql(TYPES + " WHERE ID_TYPE_SERVICE_A_DEDUIRE = :id").param("id", idType)
                .query(this::versType).optional();
    }

    private TypeService versType(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new TypeService(rs.getInt("ID_TYPE_SERVICE_A_DEDUIRE"), rs.getString("LIBELLE"), rs.getInt("SIGNE"));
    }

    @Override
    public List<ServiceEnAttente> servicesEnAttente(long codeTransporteur, YearMonth mois, String devise) {
        return jdbc.sql(EN_ATTENTE)
                .param("transporteur", codeTransporteur)
                .param("debut", mois.atDay(1))
                .param("fin", mois.plusMonths(1).atDay(1))
                .param("devise", devise)
                .query((rs, n) -> new ServiceEnAttente(
                        rs.getInt("ID_SERVICE_A_DEDUIRE_FACTURE"),
                        rs.getInt("CODE_TYPE_SERVICE_A_DEDUIRE"),
                        rs.getBigDecimal("MONTANT"),
                        rs.getTimestamp("DATE_SERVICE").toLocalDateTime().toLocalDate(),
                        rs.getString("NOTE_SERVICE")))
                .list();
    }

    @Override
    public long ajouterLigneService(EnTeteFacture f, TypeService type, BigDecimal montant,
                                    String description, String utilisateur) {
        LocalDateTime maintenant = LocalDateTime.now();
        long idLigne = jdbc.sql(INSERER_LIGNE)
                .param("numero", f.numero())
                .param("idFacture", f.id())
                .param("bon", f.premierBon())
                .param("transporteur", f.codeTransporteur())
                .param("montant", montant)
                .param("type", type.id())
                .param("description", description)
                .param("devise", f.devise())
                .param("dateGeneration", f.dateGeneration())
                .param("par", utilisateur)
                .param("le", maintenant)
                .query(Long.class).single();

        int maj = jdbc.sql(MAJ_EN_TETE)
                .param("montant", montant)
                .param("par", utilisateur)
                .param("le", maintenant)
                .param("id", f.id())
                .update();
        if (maj != 1) {
            throw new ErreurMetier("FACTURE_POSTEE", "La facture " + f.numero() + " a été postée entre-temps.");
        }
        return idLigne;
    }

    @Override
    public void enregistrerServiceFacture(EnTeteFacture f, TypeService type, BigDecimal montant,
                                          LocalDate dateService, String note, long idLigne, String utilisateur) {
        jdbc.sql(INSERER_SERVICE)
                .param("transporteur", f.codeTransporteur())
                .param("type", type.id())
                .param("date", dateService)
                .param("montant", montant)
                .param("devise", f.devise())
                .param("idLigne", idLigne)
                .param("note", note)
                .param("par", utilisateur)
                .param("le", LocalDateTime.now())
                .update();
    }

    @Override
    public void marquerServiceFacture(int idService, long idLigne, String utilisateur) {
        int maj = jdbc.sql(MARQUER_SERVICE)
                .param("idLigne", idLigne)
                .param("par", utilisateur)
                .param("le", LocalDateTime.now())
                .param("id", idService)
                .update();
        if (maj != 1) {
            throw new ErreurMetier("SERVICE_DEJA_FACTURE",
                    "Le service n° " + idService + " a déjà été incorporé à une facture.");
        }
    }
}