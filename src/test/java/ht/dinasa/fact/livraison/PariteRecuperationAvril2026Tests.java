package ht.dinasa.fact.livraison;

import ht.dinasa.fact.livraison.application.RecuperationAccpac;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
//import java.util.function.Runnable;

import static org.assertj.core.api.Assertions.assertThat;

/** Parité de la récupération : les deux exécutions sont annulées, la base ne change pas. */
@SpringBootTest
class PariteRecuperationAvril2026Tests {

    private static final LocalDate DEBUT = LocalDate.of(2026, 4, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 4, 30);

    /**
     * Un bon = une ligne de texte comparable. On compare les CODES (camion, client) et non les
     * numéros de fiche : en cas de code en double, l'ancien système choisit une fiche au hasard
     * (constaté : le choix change d'une exécution à l'autre).
     */
    private static final String INSTANTANE = """
            SELECT CONCAT(RTRIM(m.NoCommande), ' | ', m.DateMouvement,
                          ' | client ', RTRIM(LTRIM(cl.CODE_CLIENT)),
                          ' | camion ', RTRIM(LTRIM(ca.CamionCode)),
                          ' | transp ', m.CodeTransporteur,
                          ' | fact ', ISNULL(RTRIM(LTRIM(cf.CODE_CLIENT)), '-'), ' | ',
                          ISNULL((SELECT STRING_AGG(CONCAT(ISNULL(CAST(d.CodeProduit AS varchar(5)), '-'), ':',
                                                           d.VolumeCommande, '/', d.Volumefourni, '/', d.VolumeLivre), ' ')
                                         WITHIN GROUP (ORDER BY d.CodeProduit, d.VolumeCommande)
                                    FROM dbo.MouvementDetail d WHERE d.CodeMouvement = m.ID_Mouvement), 'sans détail'))
              FROM dbo.Mouvement m
              LEFT JOIN FACT.CLIENT cl ON cl.ID_CLIENT = m.CodeClient
              LEFT JOIN dbo.Camion ca  ON ca.ID_Camion = m.CodeCamion
              LEFT JOIN FACT.CLIENT cf ON cf.ID_CLIENT = NULLIF(m.CodeClientFact, 0)
             WHERE m.ID_Mouvement > :max
            """;

    @Autowired TransactionTemplate transaction;
    @Autowired JdbcClient jdbc;
    @Autowired RecuperationAccpac recuperation;

    private record Instantane(List<String> bons, long lignesMarquees, long lignesAvecClientFacture) {
    }

    private Instantane executerPuisAnnuler(Runnable execution) {
        return transaction.execute(statut -> {
            statut.setRollbackOnly();
            long max = jdbc.sql("SELECT ISNULL(MAX(ID_Mouvement), 0) FROM dbo.Mouvement").query(Long.class).single();
            execution.run();
            List<String> bons = new ArrayList<>(jdbc.sql(INSTANTANE).param("max", max).query(String.class).list());
            bons.sort(null);
            long marquees = jdbc.sql("SELECT COUNT(*) FROM dbo.Livraison_Acpacc"
                            + " WHERE DateMouvement BETWEEN :d AND :f AND IsRetreive = 1")
                    .param("d", DEBUT).param("f", FIN).query(Long.class).single();
            long clientFact = jdbc.sql("SELECT COUNT(*) FROM dbo.Livraison_Acpacc"
                            + " WHERE DateMouvement BETWEEN :d AND :f AND id_client_fact IS NOT NULL AND id_client_fact <> 0")
                    .param("d", DEBUT).param("f", FIN).query(Long.class).single();
            return new Instantane(bons, marquees, clientFact);
        });
    }

    @Test
    void la_nouvelle_recuperation_produit_exactement_les_memes_bons_que_l_ancienne() {
        Instantane ancien = executerPuisAnnuler(() ->
                jdbc.sql("SET NOCOUNT ON; EXEC FACT.PS_ACCPAC_RetreiveData_ByMonthAndYear :debut, :fin")
                        .param("debut", DEBUT).param("fin", FIN)
                        .query((rs, n) -> rs.getString(2)).single());

        Instantane nouveau = executerPuisAnnuler(() ->
                recuperation.recuperer(YearMonth.of(2026, 4), false));

        List<String> seulementAncien = new ArrayList<>(ancien.bons());
        nouveau.bons().forEach(seulementAncien::remove);
        List<String> seulementNouveau = new ArrayList<>(nouveau.bons());
        ancien.bons().forEach(seulementNouveau::remove);

        System.out.println();
        System.out.println("===== PARITÉ RÉCUPÉRATION AVRIL 2026 =====");
        System.out.println("Bons créés     : ancien " + ancien.bons().size() + " / nouveau " + nouveau.bons().size());
        System.out.println("Lignes marquées: ancien " + ancien.lignesMarquees() + " / nouveau " + nouveau.lignesMarquees());
        System.out.println("Client facturé : ancien " + ancien.lignesAvecClientFacture()
                + " / nouveau " + nouveau.lignesAvecClientFacture() + " lignes");
        System.out.println("Seulement dans l'ancien (" + seulementAncien.size() + ") :");
        seulementAncien.stream().limit(10).forEach(b -> System.out.println("   " + b));
        System.out.println("Seulement dans le nouveau (" + seulementNouveau.size() + ") :");
        seulementNouveau.stream().limit(10).forEach(b -> System.out.println("   " + b));
        System.out.println("==========================================");

        assertThat(seulementAncien).isEmpty();
        assertThat(seulementNouveau).isEmpty();
        assertThat(nouveau.lignesMarquees()).isEqualTo(ancien.lignesMarquees());
    }

    @Test
    void le_controle_signale_les_anomalies_sans_rien_ecrire() {
        var controle = recuperation.controler(YearMonth.of(2026, 4), false);

        System.out.println();
        System.out.println("===== CONTRÔLE AVRIL 2026 (rien n'est écrit) =====");
        System.out.println("Lignes à récupérer : " + controle.lignesARecuperer() + " (dont OT : " + controle.lignesOT() + ")");
        System.out.println("Bons prévus        : " + controle.bonsPrevus());
        controle.anomaliesParType().forEach((type, nb) -> System.out.println("  " + type.libelle() + " : " + nb));
        System.out.println("==================================================");

        Long marquees = jdbc.sql("SELECT COUNT(*) FROM dbo.Livraison_Acpacc"
                        + " WHERE DateMouvement BETWEEN :d AND :f AND IsRetreive = 1")
                .param("d", DEBUT).param("f", FIN).query(Long.class).single();
        assertThat(marquees).isZero();
    }
}