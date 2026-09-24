package ht.dinasa.fact.shared.erreurs;

/**
 * Erreur liée à une règle métier (ex. : "aucun tarif actif").
 * Le code permet au front React d'afficher un message adapté.
 */
public class ErreurMetier extends RuntimeException {

    private final String code;

    public ErreurMetier(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}