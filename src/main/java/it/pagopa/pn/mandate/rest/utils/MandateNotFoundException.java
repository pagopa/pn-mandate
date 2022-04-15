package it.pagopa.pn.mandate.rest.utils;

public class MandateNotFoundException extends PnException {


    public MandateNotFoundException() {
        super("Delega non presente", "Non è stata trovata nessuna delega valida", 404);
    }

}
