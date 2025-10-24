package it.pagopa.pn.ciechecker.generator.constants;

import java.time.LocalDate;

public class CieGeneratorConstants {

    public static final String MASTERLIST_NAME="new_IT_MasterListCSCA.zip";
    public static final String CA_FILENAME="catest.pem";
    public static final String KEY_FILENAME="catest.key";

    //PARAMETERS
    public static final String GIVEN_NAME = "MARIO";
    public static final String SURNAME = "ROSSI";
    public static final String DOCUMENT_NUMBER = "AA1234567";
    public static final String NATIONALITY = "ITA";
    public static final String COD_FISCALE="RSSMRA80A01H501U";
    public static final String PLACE_OF_BIRTH= "ROMA";
    public static final char SEX= 'M';
    public static final LocalDate DATE_OF_BIRTH= LocalDate.of(1980,1,1);
    public static final LocalDate EXPIRY_DATE=  LocalDate.of(2030, 12, 31);
}
