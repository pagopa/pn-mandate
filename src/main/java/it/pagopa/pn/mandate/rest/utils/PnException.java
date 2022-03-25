package it.pagopa.pn.mandate.rest.utils;

public class PnException extends RuntimeException {
    
    protected String description;

    public PnException(String message, String description) {
        super(message);
        this.description = description;
    }    
}
