package ht.dinasa.fact.livraison.application;

import ht.dinasa.fact.livraison.domain.LigneAccpac;
import ht.dinasa.fact.livraison.domain.RegleClientFacture;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SourceAccpac {

    /** Date plancher : on ne remonte jamais avant la première donnée ACCPAC (paramètre général). */
    Optional<LocalDate> premiereDateAccpac();

    /** Lignes non récupérées de la période, avec les correspondances (client, camion, transporteur, produit). */
    List<LigneAccpac> lignesARecuperer(LocalDate debut, LocalDate fin);

    /** Livraisons déjà récupérées de la période (hors OT), pour la règle du client facturé. */
    List<RegleClientFacture.Livraison> livraisonsRecuperees(LocalDate debut, LocalDate fin);
}