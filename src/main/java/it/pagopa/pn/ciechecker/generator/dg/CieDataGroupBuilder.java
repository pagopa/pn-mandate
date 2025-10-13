package it.pagopa.pn.ciechecker.generator.dg;

import org.bouncycastle.asn1.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

public class CieDataGroupBuilder {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public byte[] buildDG1(String surname,
                           String givenName,
                           String documentNumber,
                           String nationality,
                           LocalDate birthDate,
                           char sex,
                           LocalDate expiryDate){
        String line1 = String.format("I<%s%s<<%s", nationality,
                        padRight(surname, 15), padRight(givenName, 14))
                .replace(' ', '<');
        line1 = padRight(line1, 30, '<');

        String dob = birthDate.format(YYMMDD);
        String exp = expiryDate.format(YYMMDD);

        String line2 = String.format("%s<%s%s%s%s%s%s<<<<<<<<<<<<<<",
                documentNumber,
                nationality,
                dob,
                checkDigit(dob),
                sex,
                exp,
                checkDigit(exp));

        line2 = padRight(line2, 30, '<');
        String line3 = padRight("", 30, '<');

        String mrz = line1 + line2 + line3;
        return mrz.getBytes(StandardCharsets.US_ASCII);
    }
    public byte[] buildDG11(String fullName, String cf, LocalDate birthDate, String placeOfBirth, String nationality)
            throws IOException {
        Instant instant = birthDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        ASN1EncodableVector seq = new ASN1EncodableVector();
        seq.add(new DERPrintableString(fullName));
        seq.add(new DERPrintableString(cf));
        seq.add(new DERGeneralizedTime(Date.from(instant)));
        seq.add(new DERPrintableString(placeOfBirth));
        seq.add(new DERPrintableString(nationality));
        DERSequence dg11 = new DERSequence(seq);
        return dg11.getEncoded(ASN1Encoding.DER);
    }


    private String padRight(String input, int length, char pad) {
        if (input.length() >= length) return input.substring(0, length);
        char[] padArray = new char[length - input.length()];
        Arrays.fill(padArray, pad);
        return input + new String(padArray);
    }

    private String padRight(String input, int length) {
        return padRight(input, length, ' ');
    }

    private int checkDigit(String data) {
        int[] weights = {7, 3, 1};
        int sum = 0;
        int i = 0;
        for (char c : data.toCharArray()) {
            int val;
            if (Character.isDigit(c)) val = c - '0';
            else if (c >= 'A' && c <= 'Z') val = c - 'A' + 10;
            else val = 0;
            sum += val * weights[i % 3];
            i++;
        }
        return sum % 10;
    }

}
