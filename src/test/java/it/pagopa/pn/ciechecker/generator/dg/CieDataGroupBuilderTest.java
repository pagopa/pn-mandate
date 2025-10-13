package it.pagopa.pn.ciechecker.generator.dg;

import org.bouncycastle.asn1.ASN1Sequence;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CieDataGroupBuilderTest {

    private static final String NAME = "NAME";
    private static final String SURNAME = "SURNAME";
    private static final String DOC_NUMBER = "DOC_NUMBER";
    private static final String NATIONALITY = "ITA";
    private static final String CF= "RSSMRA80A01H501U";
    private static final char SEX = 'M';
    private static final String POB = "PLACEOFBIRTH";
    private static final int LENGTH =90;
    private static final LocalDate DOB = LocalDate.of(1990, 1, 1);
    private static final LocalDate EXP = LocalDate.of(2030, 1, 1);


    @Test
    public void buildDG1_validMrzFormat() {
        byte[] dg1 = buildDG1();
        String mrz = new String(dg1);
        assertEquals(LENGTH, mrz.length());
        assertTrue(mrz.startsWith("I<"+NATIONALITY));
        assertTrue(mrz.contains(SURNAME));
    }

    @Test
    public void buildDG11_asn1Parsable() throws Exception {
        byte[] dg11 = buildDG11();
        ASN1Sequence seq = ASN1Sequence.getInstance(new ByteArrayInputStream(dg11).readAllBytes());
        assertEquals(5, seq.size());
        assertTrue(seq.toString().contains(POB));
        assertTrue(seq.toString().contains(NAME));
        assertTrue(seq.toString().contains(CF));
    }


    private byte[] buildDG1(){
        CieDataGroupBuilder b = new CieDataGroupBuilder();
        return b.buildDG1(
                SURNAME,
                NAME,
                DOC_NUMBER,
                NATIONALITY,
                DOB,
                SEX,
                EXP
        );
    }

    private byte[] buildDG11() throws IOException {
        CieDataGroupBuilder b = new CieDataGroupBuilder();
        String nameSurname= NAME + " " + SURNAME;
        return b.buildDG11(
                nameSurname,
                CF,
                DOB,
                POB,
                NATIONALITY
        );
    }
}
