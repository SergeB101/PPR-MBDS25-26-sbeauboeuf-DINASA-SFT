package ht.dinasa.fact.shared.erreurs;

public class ElementIntrouvable extends ErreurMetier {

    public ElementIntrouvable(String quoi, Object identifiant) {
        super("INTROUVABLE", quoi + " introuvable : " + identifiant);
    }
}