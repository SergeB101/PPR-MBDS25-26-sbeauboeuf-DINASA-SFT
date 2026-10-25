package ht.dinasa.fact.livraison.application;

import ht.dinasa.fact.livraison.application.DepotAnomalies.BilanAnnulation;
import ht.dinasa.fact.livraison.application.DepotAnomalies.LigneACorriger;
import ht.dinasa.fact.livraison.application.LectureAnomalies.CodeEnDouble;
import ht.dinasa.fact.livraison.application.LectureAnomalies.CodesAccpac;
import ht.dinasa.fact.livraison.domain.LigneAccpac;
import ht.dinasa.fact.livraison.domain.PreparationRecuperation;
import ht.dinasa.fact.livraison.domain.TypeAnomalie;
import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.audit.EntreeAudit;
import ht.dinasa.fact.shared.audit.Journal;
import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tableau de bord des anomalies ACCPAC, correction des codes, annulation d'une récupération. */
@Service
public class AnomaliesAccpac {

    public enum TypeCorrection { CAMION, CLIENT }

    public record LigneAnomalie(long idLigne, String noFacture, LocalDate date, String codeCamion,
                                String codeClient, String shipTo, String codeProduit, BigDecimal volume,
                                List<TypeAnomalie> anomalies) {
    }

    public record Tableau(LocalDate debut, LocalDate fin, int lignesARecuperer, int bonsPrevus,
                          Map<TypeAnomalie, Long> anomaliesParType, List<LigneAnomalie> lignes,
                          List<CodeEnDouble> camionsEnDouble, List<CodeEnDouble> clientsEnDouble) {
    }

    public record ResultatCorrection(int lignesCorrigees) {
    }

    private static final CodesAccpac SANS_CODES = new CodesAccpac(null, null, null);
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SourceAccpac source;
    private final DepotRecuperation depotRecuperation;
    private final LectureAnomalies lecture;
    private final DepotAnomalies depot;
    private final Journal journal;
    private final UtilisateurCourant utilisateur;
    private final PreparationRecuperation preparation = new PreparationRecuperation();

    public AnomaliesAccpac(SourceAccpac source, DepotRecuperation depotRecuperation, LectureAnomalies lecture,
                           DepotAnomalies depot, Journal journal, UtilisateurCourant utilisateur) {
        this.source = source;
        this.depotRecuperation = depotRecuperation;
        this.lecture = lecture;
        this.depot = depot;
        this.journal = journal;
        this.utilisateur = utilisateur;
    }

    /** Comme le contrôle : la reconnaissance des codes écrit dans la table intermédiaire, donc transaction TOUJOURS annulée. */
    @Transactional
    public Tableau tableau(YearMonth mois, boolean inclureMoisPrecedent) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

        LocalDate debut = debut(mois, inclureMoisPrecedent);
        LocalDate fin = mois.atEndOfMonth();
        depotRecuperation.etablirCorrespondances(debut, fin);
        List<LigneAccpac> lignes = source.lignesARecuperer(debut, fin);
        PreparationRecuperation.Resultat r = preparation.preparer(lignes);
        Map<Long, CodesAccpac> codes = lecture.codesAccpac(debut, fin);

        List<LigneAnomalie> vue = r.anomalies().stream().map(a -> {
            LigneAccpac l = a.ligne();
            CodesAccpac c = codes.getOrDefault(l.idLigne(), SANS_CODES);
            return new LigneAnomalie(l.idLigne(), l.noFacture(), l.date(), l.codeCamion(), c.codeClient(),
                    c.shipTo(), c.codeProduit(), l.volumeFourni(), a.anomalies());
        }).toList();

        Map<TypeAnomalie, Long> parType = new TreeMap<>();
        vue.forEach(l -> l.anomalies().forEach(t -> parType.merge(t, 1L, Long::sum)));

        return new Tableau(debut, fin, lignes.size(), r.bons().size(), parType, vue,
                lecture.camionsEnDouble(debut, fin), lecture.clientsEnDouble(debut, fin));
    }

    @Transactional
    public ResultatCorrection corriger(long idLigne, TypeCorrection type, String nouveauCode, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("MOTIF_OBLIGATOIRE", "Le motif de la correction est obligatoire.");
        }
        if (type == null || nouveauCode == null || nouveauCode.isBlank()) {
            throw new ErreurMetier("CODE_OBLIGATOIRE", "Le type de correction et le nouveau code sont obligatoires.");
        }
        String code = nouveauCode.trim();
        LigneACorriger ligne = depot.ligne(idLigne)
                .orElseThrow(() -> new ElementIntrouvable("Ligne ACCPAC", idLigne));
        if (ligne.recuperee()) {
            throw new ErreurMetier("LIGNE_DEJA_RECUPEREE",
                    "Cette ligne est déjà récupérée : annulez d'abord la récupération du mois pour la corriger.");
        }
        String par = utilisateur.login();
        String ancien;
        int corrigees;

        if (type == TypeCorrection.CAMION) {
            long idCamion = depot.idCamion(code).orElseThrow(() ->
                    new ErreurMetier("CODE_INCONNU", "Le code camion « " + code + " » n'existe pas."));
            ancien = nettoyer(ligne.codeCamion());
            corrigees = depot.corrigerCamion(ligne, code, idCamion,
                    log(par, "Modification Code Camion --> '" + ancien + "' changé en --> '" + code + "'.", motif, idLigne), par);
        } else {
            long idClient = depot.idClient(code).orElseThrow(() ->
                    new ErreurMetier("CODE_INCONNU", "Le code client « " + code + " » n'existe pas."));
            ancien = nettoyer(ligne.codeClient());
            corrigees = depot.corrigerClient(ligne, code, idClient,
                    log(par, "Modification du Code Client --> '" + ancien + "' changé en --> '" + code + "'.", motif, idLigne), par);
        }

        journal.enregistrer(new EntreeAudit("ACCPAC_ANOMALIE_CORRIGEE", "LIVRAISON_ACCPAC", idLigne, null,
                motif.trim(), Map.of("type", type.name(), "code", ancien),
                Map.of("code", code, "lignesCorrigees", corrigees)));
        return new ResultatCorrection(corrigees);
    }

    @Transactional
    public BilanAnnulation annuler(YearMonth mois, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("MOTIF_OBLIGATOIRE", "Le motif de l'annulation est obligatoire.");
        }
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();
        int factures = depot.bonsDejaFactures(debut, fin);
        if (factures > 0) {
            throw new ErreurMetier("PERIODE_DEJA_FACTUREE", "Annulation impossible : " + factures
                    + " bon(s) de " + mois + " sont déjà facturés.");
        }
        BilanAnnulation bilan = depot.annuler(debut, fin);
        journal.enregistrer(new EntreeAudit("ACCPAC_ANNULATION", "PERIODE", debut + "/" + fin, null, motif.trim(),
                null, Map.of("bonsSupprimes", bilan.bonsSupprimes(), "detailsSupprimes", bilan.detailsSupprimes(),
                "lignesReinitialisees", bilan.lignesReinitialisees())));
        return bilan;
    }

    private LocalDate debut(YearMonth mois, boolean inclureMoisPrecedent) {
        LocalDate debut = (inclureMoisPrecedent ? mois.minusMonths(1) : mois).atDay(1);
        LocalDate plancher = source.premiereDateAccpac().orElse(null);
        return plancher != null && debut.isBefore(plancher) ? plancher : debut;
    }

    /** Même forme que l'historique de l'ancien système, avec le motif en plus. */
    private static String log(String par, String changement, String motif, long idLigne) {
        return "[" + par + " - " + LocalDateTime.now().format(HORODATAGE) + "] " + changement
                + " Motif : " + motif.trim() + ".  Id_livraison utilisé: " + idLigne + "\r\n";
    }

    private static String nettoyer(String code) {
        return code == null ? "" : code.trim();
    }
}