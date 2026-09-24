package ht.dinasa.fact.facturation.domain;

public enum MotifRejet {

    CLIENT_SANS_POINT_LIVRAISON("La localité (point de livraison) n'est pas renseignée pour le client facturé."),
    CAMION_PAS_AU_GALLON("Ce camion n'est pas configuré pour la facturation au gallon."),
    AUCUN_TARIF_ACTIF("Aucune tarification active n'est définie."),
    PAS_DE_PRIX_POUR_CE_POINT("La tarification active n'a pas de prix au gallon pour ce point de livraison.");

    private final String message;

    MotifRejet(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}