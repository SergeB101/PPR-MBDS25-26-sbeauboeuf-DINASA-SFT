package ht.dinasa.fact.securite.domain;

import ht.dinasa.fact.shared.erreurs.ErreurMetier;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Mêmes règles que l'ancien écran (8 car., majuscule, minuscule, chiffre, spécial) + limite BCrypt de 72 octets. */
public final class PolitiqueMotDePasse {

    private static final Pattern MAJ = Pattern.compile("[A-Z]");
    private static final Pattern MIN = Pattern.compile("[a-z]");
    private static final Pattern CHIFFRE = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9]");

    private static final String A_MAJ = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String A_MIN = "abcdefghijkmnpqrstuvwxyz";
    private static final String A_CHIFFRES = "23456789";
    private static final String A_SPECIAUX = "!@#$%*?-";
    private static final SecureRandom HASARD = new SecureRandom();

    private PolitiqueMotDePasse() {
    }

    public static void valider(String mdp) {
        boolean ok = mdp != null
                && mdp.length() >= 8
                && mdp.getBytes(StandardCharsets.UTF_8).length <= 72
                && MAJ.matcher(mdp).find() && MIN.matcher(mdp).find()
                && CHIFFRE.matcher(mdp).find() && SPECIAL.matcher(mdp).find();
        if (!ok) {
            throw new ErreurMetier("MOT_DE_PASSE_FAIBLE", "Le mot de passe doit contenir au moins 8 caractères, "
                    + "avec une majuscule, une minuscule, un chiffre et un caractère spécial.");
        }
    }

    /** 12 caractères, sans caractères ambigus (0/O, 1/l/I). */
    public static String temporaire() {
        List<Character> c = new ArrayList<>();
        c.add(tirer(A_MAJ));
        c.add(tirer(A_MIN));
        c.add(tirer(A_CHIFFRES));
        c.add(tirer(A_SPECIAUX));
        String tous = A_MAJ + A_MIN + A_CHIFFRES + A_SPECIAUX;
        while (c.size() < 12) {
            c.add(tirer(tous));
        }
        Collections.shuffle(c, HASARD);
        StringBuilder sb = new StringBuilder();
        c.forEach(sb::append);
        return sb.toString();
    }

    private static char tirer(String alphabet) {
        return alphabet.charAt(HASARD.nextInt(alphabet.length()));
    }
}