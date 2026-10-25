package ht.dinasa.fact.livraison.application;

import ht.dinasa.fact.livraison.domain.BonACreer;
import ht.dinasa.fact.livraison.domain.PreparationRecuperation;
import ht.dinasa.fact.livraison.domain.PreparationRecuperation.LigneEnAnomalie;
import ht.dinasa.fact.livraison.domain.RegleClientFacture;
import ht.dinasa.fact.livraison.domain.TypeAnomalie;
import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.audit.EntreeAudit;
import ht.dinasa.fact.shared.audit.Journal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Récupération des bons de livraison depuis la table intermédiaire ACCPAC (Sage 300). */
@Service
public class RecuperationAccpac {

    public record Periode(LocalDate debut, LocalDate fin) {
    }

    /** Résultat d'un contrôle : rien n'est conservé dans la base. */
    public record Controle(Periode periode, int lignesARecuperer, int lignesOT, int bonsPrevus,
                           Map<TypeAnomalie, Long> anomaliesParType, List<LigneEnAnomalie> anomalies) {
    }

    /** Résultat d'une récupération. */
    public record Bilan(Periode periode, int bonsCrees, int bonsOT, int lignesDetail, int lignesMarquees,
                        int bonsAvecClientFacture, Map<TypeAnomalie, Long> anomaliesRestantes) {
    }

    private final SourceAccpac source;
    private final DepotRecuperation depot;
    private final Journal journal;
    private final UtilisateurCourant utilisateur;
    private final PreparationRecuperation preparation = new PreparationRecuperation();
    private final RegleClientFacture regleClientFacture = new RegleClientFacture();

    public RecuperationAccpac(SourceAccpac source, DepotRecuperation depot, Journal journal,
                              UtilisateurCourant utilisateur) {
        this.source = source;
        this.depot = depot;
        this.journal = journal;
        this.utilisateur = utilisateur;
    }

    /**
     * Le contrôle doit reconnaître les codes comme la vraie récupération, ce qui écrit dans la table
     * intermédiaire : on le fait dans une transaction TOUJOURS ANNULÉE, rien n'est conservé.
     */
    @Transactional
    public Controle controler(YearMonth mois, boolean inclureMoisPrecedent) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

        Periode p = periode(mois, inclureMoisPrecedent);
        depot.etablirCorrespondances(p.debut(), p.fin());
        var lignes = source.lignesARecuperer(p.debut(), p.fin());
        var r = preparation.preparer(lignes);
        return new Controle(p, lignes.size(), r.lignesOT(), r.bons().size(), parType(r), r.anomalies());
    }

    @Transactional
    public Bilan recuperer(YearMonth mois, boolean inclureMoisPrecedent) {
        Periode p = periode(mois, inclureMoisPrecedent);
        String par = utilisateur.login();

        depot.memoriserDernierBonAvantRecuperation(par);
        depot.etablirCorrespondances(p.debut(), p.fin());
        var lignes = source.lignesARecuperer(p.debut(), p.fin());

        var preparationResultat = preparation.preparer(lignes);
        int details = 0;
        int bonsOT = 0;
        for (BonACreer bon : preparationResultat.bons()) {
            depot.creerBon(bon);
            details += bon.details().size();
            if (bon.ot()) bonsOT++;
        }
        int marquees = depot.marquerRecuperees(p.debut(), p.fin());

        var clients = regleClientFacture.clientsFactures(source.livraisonsRecuperees(p.debut(), p.fin()));
        int bonsClientFacture = depot.appliquerClientsFactures(clients, p.debut(), p.fin());

        depot.genererRapportOT();

        Bilan bilan = new Bilan(p, preparationResultat.bons().size(), bonsOT, details, marquees,
                bonsClientFacture, parType(preparationResultat));
        journal.enregistrer(new EntreeAudit("ACCPAC_RECUPERATION", "PERIODE", p.debut() + "/" + p.fin(),
                null, null, null,
                Map.of("bonsCrees", bilan.bonsCrees(), "bonsOT", bonsOT, "lignesDetail", details,
                        "lignesMarquees", marquees, "anomalies", bilan.anomaliesRestantes().toString())));
        return bilan;
    }

    private Periode periode(YearMonth mois, boolean inclureMoisPrecedent) {
        LocalDate debut = (inclureMoisPrecedent ? mois.minusMonths(1) : mois).atDay(1);
        LocalDate fin = mois.atEndOfMonth();
        LocalDate plancher = source.premiereDateAccpac().orElse(null);
        if (plancher != null && debut.isBefore(plancher)) {
            debut = plancher;
        }
        return new Periode(debut, fin);
    }

    private static Map<TypeAnomalie, Long> parType(PreparationRecuperation.Resultat r) {
        Map<TypeAnomalie, Long> compte = new TreeMap<>();
        r.anomalies().forEach(a -> a.anomalies().forEach(t -> compte.merge(t, 1L, Long::sum)));
        return compte;
    }
}