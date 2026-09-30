package ht.dinasa.fact.shared.audit;

import java.util.Map;

/** Une opération à tracer. « avant » et « apres » contiennent les valeurs changées. */
public record EntreeAudit(String action, String objetType, Object objetId, Long idFacture,
                          String motif, Map<String, Object> avant, Map<String, Object> apres) {
}