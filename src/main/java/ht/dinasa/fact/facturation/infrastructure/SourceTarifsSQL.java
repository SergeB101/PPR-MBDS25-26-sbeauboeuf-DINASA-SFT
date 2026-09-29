package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.SourceTarifs;
import ht.dinasa.fact.facturation.domain.GrilleTarifaire;
import ht.dinasa.fact.facturation.domain.TarifsActifs;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
class SourceTarifsSql implements SourceTarifs {

    private static final String SQL = """
            SELECT t.ID_TARIFICATION, t.CODE_TYPE_CAMION, dt.CODE_POINT_LIVRAISON, dt.PRIX_TRANSP_GALLON
              FROM FACT.TARIFICATION t
              JOIN FACT.DETAIL_TARIFICATION dt ON dt.CODE_TARIFICATION = t.ID_TARIFICATION
             WHERE t.IS_ACTIF = 1
               AND t.CODE_TYPE_CAMION IS NOT NULL
               AND dt.PRIX_TRANSP_GALLON IS NOT NULL
            """;

    private record Ligne(int idTarification, int typeCamion, int point, BigDecimal prix) {
    }

    private final JdbcClient jdbc;

    SourceTarifsSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TarifsActifs actifs() {
        List<Ligne> lignes = jdbc.sql(SQL)
                .query((rs, n) -> new Ligne(
                        rs.getInt("ID_TARIFICATION"),
                        rs.getInt("CODE_TYPE_CAMION"),
                        rs.getInt("CODE_POINT_LIVRAISON"),
                        rs.getBigDecimal("PRIX_TRANSP_GALLON")))
                .list();

        Map<Integer, Integer> tarifParType = new HashMap<>();
        Map<Integer, Map<Integer, BigDecimal>> prixParType = new HashMap<>();

        for (Ligne l : lignes) {
            Integer dejaVu = tarifParType.putIfAbsent(l.typeCamion(), l.idTarification());
            if (dejaVu != null && dejaVu != l.idTarification()) {
                throw new ErreurMetier("TARIFS_ACTIFS_MULTIPLES",
                        "Plusieurs tarifications actives pour le type de camion " + l.typeCamion()
                                + " (n° " + dejaVu + " et n° " + l.idTarification() + ").");
            }
            prixParType.computeIfAbsent(l.typeCamion(), t -> new HashMap<>()).put(l.point(), l.prix());
        }

        Map<Integer, GrilleTarifaire> grilles = new HashMap<>();
        prixParType.forEach((type, prix) ->
                grilles.put(type, new GrilleTarifaire(tarifParType.get(type), prix)));
        return new TarifsActifs(grilles);
    }
}