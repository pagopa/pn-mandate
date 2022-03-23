package it.pagopa.pn.mandate.rest.utils;

public class InvalidVerificationCodeException extends Exception {


    public InvalidVerificationCodeException() {
        super("Codice verifica non valido");        
    }

}
