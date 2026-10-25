package ht.dinasa.fact.reporting;

import ht.dinasa.fact.reporting.application.TableauxDeBord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TableauxDeBordTests {

    @Autowired TableauxDeBord tableaux;

    @Test
    void synthese_de_mars_2026() {
        var s = tableaux.synthese(YearMonth.of(2026, 3));
        System.out.println("\n===== SYNTHÈSE MARS 2026 =====");
        System.out.println("Livraisons : " + s.livraisons());
        System.out.println("Coût transport : " + s.coutTransport());
        System.out.println("En attente de posting : " + s.attentePosting());
        System.out.println("ACCPAC : " + s.accpac());

        assertThat(s.livraisons().bonsFactures()).isPositive();
        assertThat(s.coutTransport().montant()).isPositive();
        assertThat(s.attentePosting().factures()).isPositive();
    }

    @Test
    void volumes_par_mois_donne_12_lignes() {
        var v = tableaux.volumesParMois(2026);
        System.out.println("\n===== VOLUMES 2026 =====");
        v.forEach(System.out::println);
        assertThat(v).hasSize(12);
    }

    @Test
    void couts_par_transporteur_et_factures_en_attente() {
        var couts = tableaux.coutsParTransporteur(YearMonth.of(2026, 3));
        var attente = tableaux.facturesEnAttente(5);
        System.out.println("\n===== TOP 3 TRANSPORTEURS MARS 2026 =====");
        couts.stream().limit(3).forEach(System.out::println);
        System.out.println("===== 5 FACTURES LES PLUS ANCIENNES EN ATTENTE =====");
        attente.forEach(System.out::println);

        assertThat(couts).isNotEmpty();
        assertThat(attente).hasSize(5);
    }
}