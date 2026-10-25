package ht.dinasa.fact.securite.application;

/** Message volontairement identique pour toutes les raisons (on ne révèle pas si le login existe). */
public class ConnexionRefusee extends RuntimeException {

    public enum Raison { IDENTIFIANTS_INCORRECTS, COMPTE_NON_ACTIVE, COMPTE_INACTIF, COMPTE_BLOQUE }

    private final Raison raison;

    public ConnexionRefusee(Raison raison) {
        super("Accès refusé : nom d'utilisateur ou mot de passe incorrect, ou compte bloqué temporairement.");
        this.raison = raison;
    }

    public Raison raison() {
        return raison;
    }
}