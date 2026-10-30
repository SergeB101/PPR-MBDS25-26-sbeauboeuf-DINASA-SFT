package ht.dinasa.fact.securite.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DepotComptes {

    record Compte(long id, String login, String nom, String prenom, long idGroupe, boolean actif,
                  String motDePasse, boolean doitChanger, int echecs, LocalDateTime bloqueJusqua, int versionJeton) {
        public String nomComplet() {
            return ((prenom == null ? "" : prenom.trim()) + " " + (nom == null ? "" : nom.trim())).trim();
        }
    }

    record UtilisateurVue(long id, String login, String nom, String prenom, long idGroupe, String groupe,
                          boolean actif, boolean compteActive, boolean doitChangerMotDePasse,
                          LocalDateTime bloqueJusqua, LocalDateTime derniereConnexion) {
    }

    /** Vide si le login est inconnu OU porté par plusieurs fiches (doublon refusé). */
    Optional<Compte> parLogin(String login);

    Optional<Compte> parId(long id);

    List<UtilisateurVue> utilisateurs();

    /** Enregistre l'empreinte, remet les échecs à zéro et invalide les jetons déjà émis. */
    void enregistrerMotDePasse(long id, String empreinte, boolean doitChanger, LocalDateTime le, String par);

    void enregistrerEchec(long id, int echecs, boolean bloquer, LocalDateTime bloqueJusqua);

    void enregistrerSucces(long id, LocalDateTime le, String ip);

    /** Invalide tous les jetons de l'utilisateur. */
    int revoquer(long id);

    void journaliserConnexion(String login, String resultat, String ip, LocalDateTime le);

    boolean loginExiste(String login);

    /** Crée la fiche dans dbo.tbl_S_User (table de l'ancienne application) et renvoie son ID. */
    long creerUtilisateur(String login, String nom, String prenom, long idGroupe, LocalDateTime le, String par);

    void modifierUtilisateur(long id, long idGroupe, boolean actif, LocalDateTime le, String par);
}