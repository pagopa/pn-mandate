package it.pagopa.pn.mandate.rest.utils;

import it.pagopa.pn.mandate.rest.mandate.v1.dto.Problem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHelperTest {


    @Test
    void handlePnException() {

        //When
        Problem res = ExceptionHelper.handleException(new MandateNotFoundException(), HttpStatus.BAD_REQUEST);

        //Then
        assertNotNull(res);
        assertEquals("Delega non presente", res.getTitle());
        assertEquals(404, res.getStatus());
    }


    @Test
    void handleException() {

        //When
        Problem res = ExceptionHelper.handleException(new NullPointerException(), HttpStatus.BAD_REQUEST);

        //Then
        assertNotNull(res);
        assertEquals(HttpStatus.BAD_REQUEST.value(), res.getStatus());
    }
}