package ht.dinasa.fact.shared.audit;

import java.util.List;

public interface Journal {

    /** Enregistre l'opération dans la MÊME transaction : si l'opération échoue, rien n'est tracé. */
    void enregistrer(EntreeAudit entree);

    List<EntreeJournal> historiqueFacture(long idFacture);
}