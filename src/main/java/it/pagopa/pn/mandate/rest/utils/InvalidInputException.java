package it.pagopa.pn.mandate.rest.utils;

public class InvalidInputException extends PnException {


    public InvalidInputException() {
        super("Parametri non validi", "Alcuni parametri non sono validi");
    }

}
