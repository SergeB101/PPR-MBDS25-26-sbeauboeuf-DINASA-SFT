package ht.dinasa.fact.facturation.web;

import ht.dinasa.fact.facturation.application.ConsultationFactures;
import ht.dinasa.fact.facturation.application.DepotServices.TypeService;
import ht.dinasa.fact.facturation.application.FicheFacture;
import ht.dinasa.fact.facturation.application.ServicesFacture;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
class ServicesFactureController {

    record DemandeService(@NotNull Integer idType,
                          @NotNull @Positive BigDecimal montant,
                          @NotNull LocalDate dateService,
                          @Size(max = 250) String note) {
    }

    record ResultatIncorporation(int servicesIncorpores, FicheFacture facture) {
    }

    private final ServicesFacture services;
    private final ConsultationFactures consultation;

    ServicesFactureController(ServicesFacture services, ConsultationFactures consultation) {
        this.services = services;
        this.consultation = consultation;
    }

    @Operation(summary = "Types de services (signe -1 = retenue, +1 = ajout)")
    @GetMapping("/services/types")
    List<TypeService> types() {
        return services.types();
    }

    @Operation(summary = "Ajouter un service à une facture non postée (montant positif, le signe vient du type)")
    @PostMapping("/factures/{id}/services")
    FicheFacture ajouter(@PathVariable long id, @Valid @RequestBody DemandeService demande) {
        services.ajouter(id, demande.idType(), demande.montant(), demande.dateService(), demande.note());
        return consultation.detail(id);
    }

    @Operation(summary = "Incorporer les services en attente du transporteur pour le mois de la facture")
    @PostMapping("/factures/{id}/services/incorporer-en-attente")
    ResultatIncorporation incorporer(@PathVariable long id) {
        int nombre = services.incorporerEnAttente(id);
        return new ResultatIncorporation(nombre, consultation.detail(id));
    }
}