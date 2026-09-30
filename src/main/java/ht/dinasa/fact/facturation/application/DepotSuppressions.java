package ht.dinasa.fact.facturation.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface DepotSuppressions {

    enum TypeLigne { TRANSPORT, SERVICE, AUTRE }

    record Ligne(long idLigne, long idFacture, String numeroFacture, boolean facturePostee,
                 TypeLigne type, BigDecimal montant, BigDecimal quantite, Long idMouvement) {
    }

    /** La ligne et son en-tête, bloqués pour modification. */
    Optional<Ligne> lignePourSuppression(long idFacture, long idLigne);

    /** Nombre de lignes de service rattachées à ce bon (l'« artifice »), hors ligne exclue. */
    int nombreServicesRattaches(long idFacture, long idBon, long idLigneExclue);

    /** Un autre bon de transport de la même facture, pour y rattacher les services. */
    Optional<Long> autreBonDeTransport(long idFacture, long idLigneExclue, long idBonExclu);

    void rattacherServices(long idFacture, long ancienBon, long nouveauBon, String utilisateur);

    /** Libère le service éventuel, supprime la ligne et met à jour l'en-tête. */
    void supprimer(Ligne ligne, String utilisateur);

    /** Rend le bon facturable s'il n'est plus référencé par aucune ligne. */
    boolean libererBon(long idBon, String utilisateur);

    /** Supprime l'en-tête s'il n'a plus aucune ligne. */
    boolean supprimerEnTeteSiVide(long idFacture);
}