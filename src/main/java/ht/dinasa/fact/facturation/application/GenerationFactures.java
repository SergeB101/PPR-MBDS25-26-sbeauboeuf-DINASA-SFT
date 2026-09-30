package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.domain.BonDeLivraison;
import ht.dinasa.fact.facturation.domain.CalculFactureGallon;
import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import ht.dinasa.fact.shared.UtilisateurCourant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

/** Calcule ET enregistre les factures d'un mois, en une seule opération « tout ou rien ». */
@Service
public class GenerationFactures {

    private final BonsAFacturer bons;
    private final SourceTarifs tarifs;
    private final DepotFactures depot;
    private final UtilisateurCourant utilisateur;
    private final Clock horloge;
    private final CalculFactureGallon calcul = new CalculFactureGallon();

    public GenerationFactures(BonsAFacturer bons, SourceTarifs tarifs, DepotFactures depot,
                              UtilisateurCourant utilisateur, Clock horloge) {
        this.bons = bons;
        this.tarifs = tarifs;
        this.depot = depot;
        this.utilisateur = utilisateur;
        this.horloge = horloge;
    }

    /**
     * @param idsMouvements bons à facturer ; vide = tous les bons facturables du mois
     */
    @Transactional
    public ResultatCalcul generer(YearMonth mois, Collection<Long> idsMouvements) {
        List<BonDeLivraison> aFacturer = bons.duMois(mois, false);
        if (idsMouvements != null && !idsMouvements.isEmpty()) {
            aFacturer = aFacturer.stream().filter(b -> idsMouvements.contains(b.idMouvement())).toList();
        }
        ResultatCalcul resultat = calcul.calculer(aFacturer, tarifs.actifs(), LocalDateTime.now(horloge));
        if (!resultat.factures().isEmpty()) {
            depot.enregistrer(resultat.factures(), utilisateur.login());
        }
        return resultat;
    }
}