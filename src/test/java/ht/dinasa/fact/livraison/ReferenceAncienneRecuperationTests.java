package ht.dinasa.fact.livraison;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Exécute l'ANCIENNE procédure de récupération sur avril 2026 dans une transaction
 * annulée à la fin du test : on mesure ce qu'elle produit sans rien garder.
 */
@SpringBootTest
@Transactional
class ReferenceAncienneRecuperationTests {

    private static final LocalDate DEBUT = LocalDate.of(2026, 4, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 4, 30);   // l'ancien écran passe le dernier jour

    @Autowired JdbcClient jdbc;

    private long compter(String sql) {
        return jdbc.sql(sql).param("debut", DEBUT).param("fin", FIN).query(Long.class).single();
    }

    @Test
    void mesurer_la_recuperation_d_avril_2026_par_l_ancienne_procedure() {
        long maxAvant = jdbc.sql("SELECT ISNULL(MAX(ID_Mouvement), 0) FROM dbo.Mouvement").query(Long.class).single();

        // SET NOCOUNT ON : sinon le pilote reçoit des « lignes modifiées » avant le résultat
        var resultat = jdbc.sql("SET NOCOUNT ON; EXEC FACT.PS_ACCPAC_RetreiveData_ByMonthAndYear :debut, :fin")
                .param("debut", DEBUT).param("fin", FIN)
                .query((rs, n) -> rs.getLong(1) + " | " + rs.getString(2) + " | " + rs.getString(3))
                .single();

        String nouveaux = "FROM dbo.Mouvement m WHERE m.ID_Mouvement > " + maxAvant;

        System.out.println();
        System.out.println("===== RÉFÉRENCE : ANCIENNE RÉCUPÉRATION AVRIL 2026 =====");
        System.out.println("Résultat : " + resultat.replace("<br />", "\n   "));
        System.out.println("Bons créés                          : " + compter("SELECT COUNT(*) " + nouveaux));
        System.out.println("  dont camions OT                   : " + compter("SELECT COUNT(*) " + nouveaux
                + " AND EXISTS (SELECT 1 FROM dbo.Camion c WHERE c.ID_Camion = m.CodeCamion AND c.CamionCode LIKE 'OT%')"));
        System.out.println("  dont bons sans aucun détail       : " + compter("SELECT COUNT(*) " + nouveaux
                + " AND NOT EXISTS (SELECT 1 FROM dbo.MouvementDetail d WHERE d.CodeMouvement = m.ID_Mouvement)"));
        System.out.println("  dont client facturé ≠ client livré: " + compter("SELECT COUNT(*) " + nouveaux
                + " AND m.CodeClientFact IS NOT NULL AND m.CodeClientFact <> m.CodeClient"));
        System.out.println("  dont client facturé vide          : " + compter("SELECT COUNT(*) " + nouveaux
                + " AND (m.CodeClientFact IS NULL OR m.CodeClientFact = 0)"));
        System.out.println("Lignes de détail créées             : " + compter(
                "SELECT COUNT(*) FROM dbo.MouvementDetail d JOIN dbo.Mouvement m ON m.ID_Mouvement = d.CodeMouvement"
                        + " WHERE m.ID_Mouvement > " + maxAvant));
        System.out.println("  dont produit vide                 : " + compter(
                "SELECT COUNT(*) FROM dbo.MouvementDetail d JOIN dbo.Mouvement m ON m.ID_Mouvement = d.CodeMouvement"
                        + " WHERE m.ID_Mouvement > " + maxAvant + " AND d.CodeProduit IS NULL"));
        System.out.println("Lignes ACCPAC de la période         : " + compter(
                "SELECT COUNT(*) FROM dbo.Livraison_Acpacc WHERE DateMouvement BETWEEN :debut AND :fin"));
        System.out.println("  marquées récupérées               : " + compter(
                "SELECT COUNT(*) FROM dbo.Livraison_Acpacc WHERE DateMouvement BETWEEN :debut AND :fin AND IsRetreive = 1"));
        System.out.println("  restées non récupérées (hors OT)  : " + compter(
                "SELECT COUNT(*) FROM dbo.Livraison_Acpacc WHERE DateMouvement BETWEEN :debut AND :fin"
                        + " AND IsRetreive = 0 AND CodeCamion NOT LIKE 'OT%'"));
        System.out.println("=========================================================");
    }
}