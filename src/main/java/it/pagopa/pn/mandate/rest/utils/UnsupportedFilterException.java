package it.pagopa.pn.mandate.rest.utils;

public class UnsupportedFilterException extends PnException {


    public UnsupportedFilterException() {
        super("Filtr non valido", "Il filtro passato non è valido");        
    }

}
