package ht.dinasa.fact.referentiel;

import ht.dinasa.fact.referentiel.application.LectureReferentiel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReferentielTests {

    @Autowired LectureReferentiel lecture;

    @Test
    void le_type_2_est_signale_sans_tarif_actif_et_les_types_3_6_8_ont_un_tarif() {
        var types = lecture.typesCamion();
        System.out.println("\n===== TYPES DE CAMION =====");
        types.forEach(t -> System.out.println(t.id() + " " + t.libelle() + " : " + t.camions() + " camions, "
                + t.tarifsActifs() + " tarif(s) actif(s)" + (t.alerte() == null ? "" : "  ⚠ " + t.alerte())));

        assertThat(types).filteredOn(t -> t.id() == 2).singleElement()
                .satisfies(t -> assertThat(t.tarifsActifs()).isZero());
        assertThat(types).filteredOn(t -> t.id() == 3 || t.id() == 6 || t.id() == 8)
                .allSatisfy(t -> assertThat(t.tarifsActifs()).isEqualTo(1));
    }

    @Test
    void les_listes_principales_ne_sont_pas_vides() {
        var camions = lecture.camions("", null);
        System.out.println("\n===== RÉFÉRENTIEL =====");
        System.out.println("Transporteurs : " + lecture.transporteurs("").size());
        System.out.println("Camions : " + camions.size()
                + " (dont code porté par plusieurs fiches : " + camions.stream().filter(c -> c.fichesMemeCode() > 1).count() + ")");
        System.out.println("Clients : " + lecture.clients("").size());
        System.out.println("Points de livraison : " + lecture.pointsLivraison().size());
        System.out.println("Tarifs actifs : " + lecture.tarifs(true).size());

        assertThat(lecture.transporteurs("")).isNotEmpty();
        assertThat(camions).isNotEmpty();
        assertThat(lecture.prixDuTarif(17)).isNotEmpty();
    }

    @Test
    void la_recherche_filtre() {
        var c = lecture.camions("", null).getFirst();
        assertThat(lecture.camions(c.code(), null)).extracting(LectureReferentiel.Camion::code).contains(c.code());
        assertThat(lecture.camions("zz-inexistant-zz", null)).isEmpty();
    }
}