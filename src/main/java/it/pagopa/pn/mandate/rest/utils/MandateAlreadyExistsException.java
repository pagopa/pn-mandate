package it.pagopa.pn.mandate.rest.utils;

public class MandateAlreadyExistsException extends PnException {


    public MandateAlreadyExistsException() {
        super("Delega già presente", "Non è possibile creare due deleghe per lo stesso delegato");
    }

}
