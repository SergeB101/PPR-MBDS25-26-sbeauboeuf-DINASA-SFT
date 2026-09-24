package ht.dinasa.fact.shared.erreurs;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GestionnaireErreurs {

    @ExceptionHandler(ElementIntrouvable.class)
    public ProblemDetail introuvable(ElementIntrouvable e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setProperty("code", e.getCode());
        return pd;
    }

    @ExceptionHandler(ErreurMetier.class)
    public ProblemDetail metier(ErreurMetier e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        pd.setProperty("code", e.getCode());
        return pd;
    }
}