package ht.dinasa.fact.facturation.domain;

import java.util.Map;
import java.util.Optional;

/** Les tarifications actives : une par type de camion. */
public record TarifsActifs(Map<Integer, GrilleTarifaire> parTypeCamion) {

    public TarifsActifs {
        parTypeCamion = Map.copyOf(parTypeCamion);
    }

    public static TarifsActifs aucun() {
        return new TarifsActifs(Map.of());
    }

    public Optional<GrilleTarifaire> pourTypeCamion(Integer typeCamion) {
        return typeCamion == null ? Optional.empty() : Optional.ofNullable(parTypeCamion.get(typeCamion));
    }
}