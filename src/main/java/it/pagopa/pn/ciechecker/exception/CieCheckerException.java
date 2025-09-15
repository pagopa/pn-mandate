package it.pagopa.pn.ciechecker.exception;

import it.pagopa.pn.ciechecker.model.ResultCieChecker;

public class CieCheckerException extends RuntimeException{

    private ResultCieChecker result;

    public CieCheckerException(){super();}
    public CieCheckerException(String message){super(message);}
    public CieCheckerException(Exception cause){super(cause);}

    public CieCheckerException(ResultCieChecker result) {
        super(result.getValue());
        this.result = result;
    }

    public CieCheckerException(ResultCieChecker result, Exception cause) {
        super(result.getValue(), cause);
        this.result = result;
    }

    public ResultCieChecker getResult() {
        return result;
    }

}