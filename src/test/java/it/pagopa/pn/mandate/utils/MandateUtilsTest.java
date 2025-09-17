package it.pagopa.pn.mandate.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MandateUtilsTest {

    @Test
    void generateRandomCode_shouldReturnFiveDigitString() {
        MandateUtils utils = new MandateUtils();
        String code = utils.generateRandomCode();

        assertNotNull(code);
        assertEquals(5, code.length());
        assertTrue(code.matches("\\d{5}"));
    }

    @Test
    void generateRandomCode_shouldReturnDifferentCodes() {
        MandateUtils utils = new MandateUtils();
        String code1 = utils.generateRandomCode();
        String code2 = utils.generateRandomCode();

        // Non è garantito che siano diversi, ma è molto probabile
        assertNotEquals(code1, code2);
    }
}
