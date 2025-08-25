package it.pagopa.pn.ciechecker.exception;

public class CieCheckerException extends RuntimeException{
    public CieCheckerException(){super();}
    public CieCheckerException(String message){super(message);}
    public CieCheckerException(Exception cause){super(cause);}

}