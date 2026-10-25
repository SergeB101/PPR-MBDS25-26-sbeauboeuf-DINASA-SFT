package ht.dinasa.fact.livraison.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prépare une récupération : bons à créer et anomalies. Rien n'est écrit. */
public class PreparationRecuperation {

    public record LigneEnAnomalie(LigneAccpac ligne, List<TypeAnomalie> anomalies) {
    }

    public record Resultat(List<BonACreer> bons, List<LigneEnAnomalie> anomalies, int lignesOT) {

        public List<LigneEnAnomalie> bloquantes() {
            return anomalies.stream()
                    .filter(a -> a.anomalies().stream().anyMatch(TypeAnomalie::bloquante))
                    .toList();
        }
    }

    private record Cle(String noFacture, LocalDate date, long client, long camion, long transporteur, boolean ot) {
    }

    public Resultat preparer(List<LigneAccpac> lignes) {
        Map<Cle, List<LigneAccpac>> groupes = new LinkedHashMap<>();
        List<LigneEnAnomalie> anomalies = new ArrayList<>();
        int lignesOT = 0;

        for (LigneAccpac l : lignes) {
            boolean ot = l.estOT();
            if (ot) {
                lignesOT++;
            }
            List<TypeAnomalie> types = l.anomalies();
            if (!ot && !types.isEmpty()) {
                anomalies.add(new LigneEnAnomalie(l, types));
            }
            if (!l.estRecuperable()) {
                continue;
            }
            Cle cle = new Cle(l.noFacture().trim(), l.date(), l.idClient(), l.idCamion(), l.idTransporteur(), ot);
            groupes.computeIfAbsent(cle, c -> new ArrayList<>()).add(l);
        }

        List<BonACreer> bons = new ArrayList<>();
        groupes.forEach((cle, groupe) -> bons.add(new BonACreer(
                cle.noFacture(), cle.date(), cle.client(), cle.camion(), cle.transporteur(), cle.ot(),
                cle.ot() ? List.of() : groupe.stream()
                        .map(l -> new BonACreer.Detail(l.idProduit(), l.volumeFourni(), l.volumeFourni(), l.volumeLivre()))
                        .toList(),
                groupe.stream().map(LigneAccpac::idLigne).toList())));

        return new Resultat(bons, anomalies, lignesOT);
    }
}