package ht.dinasa.fact.livraison.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Chargement partagé (« splitload ») : quand un camion livre plusieurs clients le même jour,
 * tous ses bons sont facturés au client dont le point de livraison est LE PLUS ÉLOIGNÉ.
 * En cas d'égalité : le plus grand numéro de client (comme l'ancienne procédure, MAX(ID_CLIENT)).
 * Si aucune distance n'est connue : pas de client facturé (on garde le client livré).
 */
public class RegleClientFacture {

    public record Livraison(long idCamion, LocalDate date, long idClient, BigDecimal distance) {
    }

    public record CamionJour(long idCamion, LocalDate date) {
    }

    public Map<CamionJour, Long> clientsFactures(List<Livraison> livraisons) {
        Map<CamionJour, List<Livraison>> parCamionJour = livraisons.stream()
                .collect(Collectors.groupingBy(l -> new CamionJour(l.idCamion(), l.date())));

        Map<CamionJour, Long> resultat = new HashMap<>();
        parCamionJour.forEach((cle, groupe) -> {
            BigDecimal distanceMax = groupe.stream()
                    .map(Livraison::distance).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            if (distanceMax == null) {
                return;
            }
            groupe.stream()
                    .filter(l -> l.distance() != null && l.distance().compareTo(distanceMax) == 0)
                    .map(Livraison::idClient)
                    .max(Comparator.naturalOrder())
                    .ifPresent(client -> resultat.put(cle, client));
        });
        return resultat;
    }
}