package it.pagopa.pn.mandate.rest.utils;

import org.springframework.http.HttpStatus;

import it.pagopa.pn.mandate.rest.mandate.v1.dto.Fault;

public class ExceptionHelper {
    
    
    public static Fault handleException(Throwable ex, HttpStatus statusError, String path){
        //TODO gestione exception e generazione fault
        Fault res = new Fault();
        res.setType(path);
        res.setStatus(statusError.value());
        if (ex instanceof PnException)
        {
            res.setTitle(ex.getMessage());
            res.setDetail(((PnException)ex).getDescription());
        }
        else
        {
            // nascondo all'utente l'errore
            res.title("Errore generico");
            res.detail("Qualcosa è andato storto, ritenta più tardi");
        }

        return res;
    }
}
