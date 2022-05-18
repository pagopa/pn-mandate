package it.pagopa.pn.mandate.rest.utils;

public class InternalErrorException extends PnException {


    public InternalErrorException() {
        super("Errore interno", "Errore applicativo interno", 500);
    }

}
