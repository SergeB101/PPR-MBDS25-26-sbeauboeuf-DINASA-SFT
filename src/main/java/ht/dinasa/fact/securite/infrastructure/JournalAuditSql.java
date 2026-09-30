package ht.dinasa.fact.securite.infrastructure;

import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.audit.EntreeAudit;
import ht.dinasa.fact.shared.audit.EntreeJournal;
import ht.dinasa.fact.shared.audit.Journal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Repository
class JournalAuditSql implements Journal {

    private static final String INSERER = """
            INSERT INTO APP.JOURNAL_AUDIT (UTILISATEUR, ACTION, OBJET_TYPE, OBJET_ID, ID_FACTURE, MOTIF, AVANT, APRES)
            VALUES (:utilisateur, :action, :objetType, :objetId, :idFacture, :motif, :avant, :apres)
            """;

    private static final String HISTORIQUE = """
            SELECT DATE_OPERATION, UTILISATEUR, ACTION, OBJET_TYPE, OBJET_ID, MOTIF, AVANT, APRES
              FROM APP.JOURNAL_AUDIT
             WHERE ID_FACTURE = :id
             ORDER BY DATE_OPERATION, ID_JOURNAL
            """;

    private final JdbcClient jdbc;
    private final UtilisateurCourant utilisateur;
    private final JsonMapper json;

    JournalAuditSql(JdbcClient jdbc, UtilisateurCourant utilisateur, JsonMapper json) {
        this.jdbc = jdbc;
        this.utilisateur = utilisateur;
        this.json = json;
    }

    @Override
    public void enregistrer(EntreeAudit e) {
        jdbc.sql(INSERER)
                .param("utilisateur", utilisateur.login())
                .param("action", e.action())
                .param("objetType", e.objetType())
                .param("objetId", String.valueOf(e.objetId()))
                .param("idFacture", e.idFacture())
                .param("motif", e.motif())
                .param("avant", enJson(e.avant()))
                .param("apres", enJson(e.apres()))
                .update();
    }

    @Override
    public List<EntreeJournal> historiqueFacture(long idFacture) {
        return jdbc.sql(HISTORIQUE).param("id", idFacture)
                .query((rs, n) -> new EntreeJournal(
                        rs.getTimestamp("DATE_OPERATION").toLocalDateTime(),
                        rs.getString("UTILISATEUR"), rs.getString("ACTION"), rs.getString("OBJET_TYPE"),
                        rs.getString("OBJET_ID"), rs.getString("MOTIF"),
                        rs.getString("AVANT"), rs.getString("APRES")))
                .list();
    }

    private String enJson(Map<String, Object> valeurs) {
        return valeurs == null || valeurs.isEmpty() ? null : json.writeValueAsString(valeurs);
    }
}