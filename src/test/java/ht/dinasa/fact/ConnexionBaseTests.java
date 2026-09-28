package ht.dinasa.fact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConnexionBaseTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void les_factures_sont_lisibles() {
        Long nb = jdbc.queryForObject("SELECT COUNT(*) FROM FACT.DETAIL_FACTURE", Long.class);
        System.out.println("Lignes de factures : " + nb);
        assertThat(nb).isPositive();
    }

    @Test
    void les_bons_de_livraison_sont_lisibles() {
        Long nb = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Mouvement", Long.class);
        System.out.println("Mouvements : " + nb);
        assertThat(nb).isPositive();
    }

    @Test
    void le_code_1_correspond_au_gallon() {
        String methode = jdbc.queryForObject(
                "SELECT METHODE FROM FACT.METHODE_TARIFICATION WHERE ID_METHODE_TARIFICATION = 1", String.class);
        assertThat(methode).isEqualTo("PAR GALLON");
    }
}