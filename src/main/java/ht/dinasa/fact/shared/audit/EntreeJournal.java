package ht.dinasa.fact.shared.audit;

import java.time.LocalDateTime;

/** Une ligne lue dans le journal. */
public record EntreeJournal(LocalDateTime date, String utilisateur, String action, String objetType,
                            String objetId, String motif, String avant, String apres) {
}