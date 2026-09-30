package ht.dinasa.fact.shared;

/** L'utilisateur qui effectue l'opération (fourni par le module sécurité). */
public interface UtilisateurCourant {

    /** Identifiant de connexion, 30 caractères maximum (limite des colonnes ModifBy). */
    String login();
}