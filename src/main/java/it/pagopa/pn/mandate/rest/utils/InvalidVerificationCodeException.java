package it.pagopa.pn.mandate.rest.utils;

public class InvalidVerificationCodeException extends PnException {


    public InvalidVerificationCodeException() {
        super("Codice verifica non valido", "Il codice passato non è corretto");        
    }

}
