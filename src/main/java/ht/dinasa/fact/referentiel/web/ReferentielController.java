package ht.dinasa.fact.referentiel.web;

import ht.dinasa.fact.referentiel.application.LectureReferentiel;
import ht.dinasa.fact.referentiel.application.LectureReferentiel.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/referentiel")
class ReferentielController {

    private final LectureReferentiel lecture;

    ReferentielController(LectureReferentiel lecture) {
        this.lecture = lecture;
    }

    @Operation(summary = "Transporteurs (fiches Personne ayant au moins un camion)")
    @GetMapping("/transporteurs")
    List<Transporteur> transporteurs(@RequestParam(required = false) String recherche) {
        return lecture.transporteurs(recherche);
    }

    @Operation(summary = "Camions (recherche sur code ou immatriculation)")
    @GetMapping("/camions")
    List<Camion> camions(@RequestParam(required = false) String recherche,
                         @RequestParam(required = false) Long idTransporteur) {
        return lecture.camions(recherche, idTransporteur);
    }

    @Operation(summary = "Clients (recherche sur code ou nom)")
    @GetMapping("/clients")
    List<Client> clients(@RequestParam(required = false) String recherche) {
        return lecture.clients(recherche);
    }

    @Operation(summary = "Points de livraison et distances")
    @GetMapping("/points-livraison")
    List<PointLivraison> pointsLivraison() {
        return lecture.pointsLivraison();
    }

    @Operation(summary = "Types de camion, avec alerte si aucun ou plusieurs tarifs actifs")
    @GetMapping("/types-camion")
    List<TypeCamion> typesCamion() {
        return lecture.typesCamion();
    }

    @Operation(summary = "Grilles de tarifs")
    @GetMapping("/tarifs")
    List<Tarif> tarifs(@RequestParam(defaultValue = "false") boolean actifsSeulement) {
        return lecture.tarifs(actifsSeulement);
    }

    @Operation(summary = "Prix au gallon d'une grille, par point de livraison")
    @GetMapping("/tarifs/{id}/prix")
    List<PrixTarif> prix(@PathVariable int id) {
        return lecture.prixDuTarif(id);
    }
}