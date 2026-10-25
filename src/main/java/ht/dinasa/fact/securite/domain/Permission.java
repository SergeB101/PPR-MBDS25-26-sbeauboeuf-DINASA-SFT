package ht.dinasa.fact.securite.domain;

import java.util.Arrays;
import java.util.Optional;

public enum Permission {
    FACTURES_CONSULTER("Consulter les factures, l'aperçu et l'historique"),
    FACTURES_GENERER("Générer les factures du mois"),
    FACTURES_MODIFIER("Ajouter des services, corriger ou supprimer une ligne"),
    FACTURES_POSTER("Poster une facture"),
    ACCPAC_CONSULTER("Voir le contrôle et les anomalies ACCPAC"),
    ACCPAC_RECUPERER("Lancer la récupération des bons ACCPAC"),
    ACCPAC_CORRIGER("Corriger un code camion ou client ACCPAC"),
    ACCPAC_ANNULER("Annuler la récupération d'un mois"),
    REFERENTIEL_CONSULTER("Consulter transporteurs, camions, clients et tarifs"),
    TABLEAU_DE_BORD_CONSULTER("Voir les tableaux de bord"),
    SECURITE_ADMINISTRER("Gérer les accès : mots de passe, droits des groupes");

    private final String libelle;

    Permission(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    public static Optional<Permission> depuis(String code) {
        return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
    }
}