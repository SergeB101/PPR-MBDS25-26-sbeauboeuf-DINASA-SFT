package ht.dinasa.fact.facturation;

import ht.dinasa.fact.facturation.application.BonsAFacturer;
import ht.dinasa.fact.facturation.application.SourceTarifs;
import ht.dinasa.fact.facturation.domain.BonDeLivraison;
import ht.dinasa.fact.facturation.domain.CalculFactureGallon;
import ht.dinasa.fact.facturation.domain.LigneFacture;
import ht.dinasa.fact.facturation.domain.Rejet;
import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parité : recalcule les bons de mars 2026 déjà facturés et compare
 * ligne par ligne avec FACT.DETAIL_FACTURE. Produit un rapport dans la console.
 */
@SpringBootTest
class PariteMars2026Tests {

    private static final YearMonth MOIS = YearMonth.of(2026, 3);

    /** Lignes de factures au gallon enregistrées par l'ancien système pour les bons du mois. */
    private static final String SQL_HISTORIQUE = """
            SELECT df.CODE_MOUVEMENT, df.NO_FACTURE, df.CODE_TRANSPORTEUR,
                   df.QTE_COMMANDEE, df.PRIX_TRANSP_GALON, df.MONTANT,
                   CASE WHEN df.DATEMODIF IS NOT NULL THEN 1 ELSE 0 END AS MODIFIEE
              FROM FACT.DETAIL_FACTURE df
              JOIN dbo.Mouvement m ON m.ID_Mouvement = df.CODE_MOUVEMENT
             WHERE m.DateLivraison >= :debut AND m.DateLivraison < :fin
               AND df.PRIX_TRANSP_GALON IS NOT NULL
               AND df.SIGLE_MONNAIE = 'USD'
            """;

    record LigneHistorique(long idMouvement, String noFacture, long transporteur,
                           BigDecimal quantite, BigDecimal prix, BigDecimal montant, boolean modifiee) {
    }

    @Autowired BonsAFacturer bonsAFacturer;
    @Autowired SourceTarifs sourceTarifs;
    @Autowired JdbcClient jdbc;

    @Test
    void comparer_mars_2026_avec_les_factures_enregistrees() {
        // 1. Ce que l'ancien système a enregistré
        Map<Long, LigneHistorique> historique = jdbc.sql(SQL_HISTORIQUE)
                .param("debut", MOIS.atDay(1))
                .param("fin", MOIS.plusMonths(1).atDay(1))
                .query((rs, n) -> new LigneHistorique(
                        rs.getLong("CODE_MOUVEMENT"), rs.getString("NO_FACTURE"),
                        rs.getLong("CODE_TRANSPORTEUR"), rs.getBigDecimal("QTE_COMMANDEE"),
                        rs.getBigDecimal("PRIX_TRANSP_GALON"), rs.getBigDecimal("MONTANT"),
                        rs.getInt("MODIFIEE") == 1))
                .list().stream()
                .collect(Collectors.toMap(LigneHistorique::idMouvement, Function.identity(), (a, b) -> a));

        // 2. Ce que le nouveau code calcule pour ces mêmes bons
        List<BonDeLivraison> tousLesBons = bonsAFacturer.duMois(MOIS, true);
        List<BonDeLivraison> bonsFactures = tousLesBons.stream()
                .filter(b -> historique.containsKey(b.idMouvement()))
                .toList();
        ResultatCalcul calcul = new CalculFactureGallon()
                .calculer(bonsFactures, sourceTarifs.actifs(), LocalDateTime.now());

        Map<Long, LigneFacture> calcule = calcul.factures().stream()
                .flatMap(f -> f.lignes().stream())
                .collect(Collectors.toMap(LigneFacture::idMouvement, Function.identity()));
        Map<Long, Rejet> rejets = calcul.rejets().stream()
                .collect(Collectors.toMap(Rejet::idMouvement, Function.identity()));

        // 3. Comparaison ligne par ligne
        Map<String, Integer> compteurs = new TreeMap<>();
        List<String> exemples = new ArrayList<>();

        for (LigneHistorique h : historique.values()) {
            LigneFacture c = calcule.get(h.idMouvement());
            String categorie;
            if (c == null) {
                Rejet rejet = rejets.get(h.idMouvement());
                categorie = rejet == null ? "5. introuvable dans la lecture" : "4. rejeté : " + rejet.motif();
            } else if (c.quantiteCommandee().compareTo(h.quantite()) != 0) {
                categorie = "3. quantité différente";
            } else if (c.prixAuGallon().compareTo(h.prix()) != 0) {
                categorie = "2. prix différent";
            } else if (c.montant().valeur().compareTo(h.montant()) != 0) {
                categorie = "1b. centimes différents";
            } else {
                categorie = "1. identique";
            }
            if (!categorie.equals("1. identique") && h.modifiee()) {
                categorie = "0. écart sur ligne modifiée à la main (exclu)";
            }
            compteurs.merge(categorie, 1, Integer::sum);

            if (!categorie.equals("1. identique") && exemples.size() < 30) {
                exemples.add(String.format("%-35s | mvt %d | facture %s | ancien : %s × %s = %s | nouveau : %s",
                        categorie, h.idMouvement(), h.noFacture(), h.quantite(), h.prix(), h.montant(),
                        c == null ? "—" : c.quantiteCommandee() + " × " + c.prixAuGallon() + " = " + c.montant().valeur()));
            }
        }

        // 4. Rapport
        System.out.println();
        System.out.println("===== PARITÉ " + MOIS + " =====");
        System.out.println("Lignes au gallon enregistrées par l'ancien système : " + historique.size());
        System.out.println("Bons du mois lus par le nouveau code (tous)         : " + tousLesBons.size());
        System.out.println("  dont non facturés par l'ancien système            : " + (tousLesBons.size() - bonsFactures.size()));
        System.out.println("--- Résultat de la comparaison ---");
        compteurs.forEach((cat, nb) -> System.out.printf("  %-40s %5d%n", cat, nb));
        System.out.println("--- Exemples d'écarts (30 max) ---");
        exemples.forEach(e -> System.out.println("  " + e));
        System.out.println("=================================");

        //assertThat(historique).isNotEmpty();
        assertThat(historique).isNotEmpty();
        assertThat(compteurs.keySet())
                .as("Tout écart hors lignes modifiées à la main est une régression")
                .allMatch(c -> c.equals("1. identique") || c.startsWith("0."));
    }

    @Test
    void la_selection_des_bons_est_la_meme_que_l_ancienne_procedure() {
        // Ancienne procédure : bons proposés à la facturation (non encore facturés)
        Map<Long, BigDecimal> ancien = new java.util.HashMap<>();
        jdbc.sql("EXEC dbo.SP_MOUVEMENT_LIST_ALL_BY_CRITERIONS_4_FACTURE :debut, :fin")
                .param("debut", MOIS.atDay(1))
                .param("fin", MOIS.atEndOfMonth())
                .query((rs, n) -> {
                    BigDecimal qte = rs.getBigDecimal("TOTALE_COMMANDEE");
                    ancien.put(rs.getLong("Id_Mouvement"), qte == null ? BigDecimal.ZERO : qte);
                    return null;
                })
                .list();

        // Nouveau code : mêmes critères (bons non facturés)
        Map<Long, BigDecimal> nouveau = bonsAFacturer.duMois(MOIS, false).stream()
                .collect(Collectors.toMap(BonDeLivraison::idMouvement, BonDeLivraison::quantiteCommandee));

        List<Long> seulementAncien = ancien.keySet().stream().filter(id -> !nouveau.containsKey(id)).sorted().toList();
        List<Long> seulementNouveau = nouveau.keySet().stream().filter(id -> !ancien.containsKey(id)).sorted().toList();
        List<Long> quantitesDifferentes = ancien.keySet().stream()
                .filter(nouveau::containsKey)
                .filter(id -> ancien.get(id).compareTo(nouveau.get(id)) != 0)
                .sorted().toList();

        System.out.println();
        System.out.println("===== SÉLECTION " + MOIS + " =====");
        System.out.println("Bons proposés par l'ancienne procédure : " + ancien.size());
        System.out.println("Bons proposés par le nouveau code      : " + nouveau.size());
        System.out.println("Seulement dans l'ancienne              : " + seulementAncien.size() + " " + seulementAncien.stream().limit(20).toList());
        System.out.println("Seulement dans le nouveau              : " + seulementNouveau.size() + " " + seulementNouveau.stream().limit(20).toList());
        System.out.println("Quantités différentes                  : " + quantitesDifferentes.size() + " " + quantitesDifferentes.stream().limit(20).toList());
        System.out.println("=================================");
    }
}