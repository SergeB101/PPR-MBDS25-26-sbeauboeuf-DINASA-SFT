package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.RegistrePosting;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
class RegistrePostingSql implements RegistrePosting {

    private static final String ETAT = """
            SELECT f.ID_FACTURE, f.NO_FACTURE, f.POSTE_YN, f.MONTANT,
                   ISNULL(s.somme, 0) AS SOMME_LIGNES, ISNULL(s.nb, 0) AS NB_LIGNES
              FROM FACT.FACTURE f WITH (UPDLOCK)
             OUTER APPLY (SELECT SUM(d.MONTANT) AS somme, COUNT(*) AS nb
                            FROM FACT.DETAIL_FACTURE d
                           WHERE d.CODE_FACTURE = f.ID_FACTURE) s
             WHERE f.ID_FACTURE = :id
            """;

    private static final String MARQUER = """
            UPDATE FACT.FACTURE
               SET POSTE_YN = 1, MODIFBY = :par, DATEMODIF = :le
             WHERE ID_FACTURE = :id AND POSTE_YN = 0
            """;

    private final JdbcClient jdbc;

    RegistrePostingSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EtatFacture> etat(long idFacture) {
        return jdbc.sql(ETAT).param("id", idFacture)
                .query((rs, n) -> new EtatFacture(
                        rs.getLong("ID_FACTURE"),
                        rs.getString("NO_FACTURE"),
                        rs.getBoolean("POSTE_YN"),
                        rs.getBigDecimal("MONTANT"),
                        rs.getBigDecimal("SOMME_LIGNES"),
                        rs.getInt("NB_LIGNES")))
                .optional();
    }

    @Override
    public boolean marquerPostee(long idFacture, String utilisateur) {
        return jdbc.sql(MARQUER)
                .param("id", idFacture)
                .param("par", utilisateur)
                .param("le", LocalDateTime.now())
                .update() == 1;
    }
}