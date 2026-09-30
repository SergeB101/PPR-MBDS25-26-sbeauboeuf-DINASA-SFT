package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.application.DepotServices.EnTeteFacture;
import ht.dinasa.fact.facturation.application.DepotServices.ServiceEnAttente;
import ht.dinasa.fact.facturation.application.DepotServices.TypeService;
import ht.dinasa.fact.shared.Devise;
import ht.dinasa.fact.shared.Montant;
import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Services à déduire (ou à ajouter) sur une facture non postée. */
@Service
public class ServicesFacture {

    private final DepotServices depot;
    private final UtilisateurCourant utilisateur;

    public ServicesFacture(DepotServices depot, UtilisateurCourant utilisateur) {
        this.depot = depot;
        this.utilisateur = utilisateur;
    }

    @Transactional(readOnly = true)
    public List<TypeService> types() {
        return depot.typesServices();
    }

    @Transactional
    public void ajouter(long idFacture, int idType, BigDecimal montantSaisi, LocalDate dateService, String note) {
        if (montantSaisi == null || montantSaisi.signum() <= 0) {
            throw new ErreurMetier("MONTANT_OBLIGATOIRE", "Le montant doit être supérieur à 0.");
        }
        if (dateService == null) {
            throw new ErreurMetier("DATE_OBLIGATOIRE", "La date du service est obligatoire.");
        }
        EnTeteFacture facture = factureModifiable(idFacture);
        TypeService type = typeValide(idType);
        BigDecimal montant = montantSigne(montantSaisi, type, facture);
        String par = utilisateur.login();

        long idLigne = depot.ajouterLigneService(facture, type, montant, note, par);
        depot.enregistrerServiceFacture(facture, type, montant, dateService, note, idLigne, par);
    }

    /** @return le nombre de services incorporés */
    @Transactional
    public int incorporerEnAttente(long idFacture) {
        EnTeteFacture facture = factureModifiable(idFacture);
        List<ServiceEnAttente> enAttente = depot.servicesEnAttente(
                facture.codeTransporteur(), YearMonth.from(facture.dateGeneration()), facture.devise());
        String par = utilisateur.login();

        for (ServiceEnAttente service : enAttente) {
            TypeService type = typeValide(service.idType());
            // comme l'ancien système : valeur absolue saisie, puis signe du type
            BigDecimal montant = montantSigne(service.montant().abs(), type, facture);
            long idLigne = depot.ajouterLigneService(facture, type, montant, service.note(), par);
            depot.marquerServiceFacture(service.id(), idLigne, par);
        }
        return enAttente.size();
    }

    private EnTeteFacture factureModifiable(long idFacture) {
        EnTeteFacture f = depot.enTetePourModification(idFacture)
                .orElseThrow(() -> new ElementIntrouvable("Facture", idFacture));
        if (f.postee()) {
            throw new ErreurMetier("FACTURE_POSTEE",
                    "La facture " + f.numero() + " est postée : elle ne peut plus être modifiée.");
        }
        if (f.premierBon() == null) {
            throw new ErreurMetier("FACTURE_VIDE",
                    "La facture " + f.numero() + " n'a aucune ligne de transport.");
        }
        if (f.devise() == null) {
            throw new ErreurMetier("FACTURE_SANS_DEVISE", "La facture " + f.numero() + " n'a pas de devise.");
        }
        return f;
    }

    private TypeService typeValide(int idType) {
        TypeService type = depot.typeService(idType)
                .orElseThrow(() -> new ElementIntrouvable("Type de service", idType));
        if (type.signe() != 1 && type.signe() != -1) {
            throw new ErreurMetier("TYPE_SERVICE_SANS_SIGNE",
                    "Le type de service « " + type.libelle() + " » n'a pas de signe (+1 ou -1) défini.");
        }
        return type;
    }

    /** Applique le signe du type et la règle des centimes (coupure). */
    private static BigDecimal montantSigne(BigDecimal montant, TypeService type, EnTeteFacture facture) {
        return new Montant(montant.multiply(BigDecimal.valueOf(type.signe())),
                Devise.valueOf(facture.devise().trim())).valeur();
    }
}