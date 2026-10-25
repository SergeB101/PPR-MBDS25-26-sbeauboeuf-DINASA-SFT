package ht.dinasa.fact.livraison.domain;

public enum TypeAnomalie {

    CAMION_NON_PRECISE("Camion non précisé dans ACCPAC", true),
    CAMION_INCONNU("Camion inconnu dans la base", true),
    CLIENT_INCONNU("Client inconnu dans la base", true),
    TRANSPORTEUR_INCONNU("Le camion n'a pas de transporteur connu", true),
    PRODUIT_INCONNU("Produit inconnu (le bon est créé quand même)", false),
    NUMERO_FACTURE_ABSENT("Numéro de facture ACCPAC absent", true);

    private final String libelle;
    private final boolean bloquante;

    TypeAnomalie(String libelle, boolean bloquante) {
        this.libelle = libelle;
        this.bloquante = bloquante;
    }

    public String libelle() {
        return libelle;
    }

    /** Une anomalie bloquante empêche la création du bon. */
    public boolean bloquante() {
        return bloquante;
    }
}