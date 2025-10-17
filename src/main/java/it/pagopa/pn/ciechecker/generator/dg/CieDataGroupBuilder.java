package it.pagopa.pn.ciechecker.generator.dg;

import com.payneteasy.tlv.BerTag;
import com.payneteasy.tlv.BerTlvBuilder;
import org.bouncycastle.asn1.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class CieDataGroupBuilder {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    public byte[] buildDG1(String surname,
                           String givenName,
                           String documentNumber,
                           String nationality,
                           LocalDate birthDate,
                           char sex,
                           LocalDate expiryDate) {

        String mrz = buildMrz(
                "I<",
                nationality,            // (issuing state)
                documentNumber,
                birthDate,
                sex,
                expiryDate,
                nationality,
                surname,
                givenName,
                "",
                ""
        );

        BerTag T_DG1 = new BerTag(0x61);
        BerTag T_MRZ = new BerTag(0x5F, 0x1F);

        BerTlvBuilder inner = new BerTlvBuilder();
        inner.addBytes(T_MRZ, mrz.getBytes(StandardCharsets.US_ASCII));

        BerTlvBuilder outer = new BerTlvBuilder();
        outer.addBytes(T_DG1, inner.buildArray());

        return outer.buildArray();
    }

    public String buildMrz(String docCode,
                           String issuingState,
                           String documentNumber,
                           LocalDate birthDate,
                           char sex,
                           LocalDate expiryDate,
                           String nationality,
                           String surname,
                           String givenNames,
                           String optional1,
                           String optional2) {

        String docCode2 = padRight(nz(docCode).toUpperCase().replace(' ', '<'), 2, '<').substring(0, 2);
        String issuing3 = padRight(nz(issuingState).toUpperCase(), 3, '<').substring(0, 3);
        String nation3 = padRight(nz(nationality).toUpperCase(), 3, '<').substring(0, 3);

        String docNum9 = padRight(nz(documentNumber).toUpperCase().replace(' ', '<'), 9, '<').substring(0, 9);
        String dobYYMMDD = birthDate.format(YYMMDD);
        String expYYMMDD = expiryDate.format(YYMMDD);
        char sexMrz = (sex == 'M' || sex == 'F') ? sex : '<';

        // LINE 1
        char cdDoc = (char) ('0' + checkDigit(docNum9));
        String line1 = docCode2 + issuing3 + docNum9 + cdDoc + padRight(sanitizeOpt(optional1), 15, '<');
        line1 = padRight(line1, 30, '<').substring(0, 30);

        // LINE 2
        char cdDob = (char) ('0' + checkDigit(dobYYMMDD));
        char cdExp = (char) ('0' + checkDigit(expYYMMDD));
        String opt2_11 = padRight(sanitizeOpt(optional2), 11, '<');

        String body2 = dobYYMMDD + cdDob + sexMrz + expYYMMDD + cdExp + nation3 + opt2_11;
        String compositeInput = docNum9 + cdDoc + dobYYMMDD + cdDob + expYYMMDD + cdExp + opt2_11;
        char cdComposite = (char) ('0' + checkDigit(compositeInput));
        String line2 = body2.substring(0, 29) + cdComposite;
        line2 = padRight(line2, 30, '<').substring(0, 30);

        // LINE 3
        String nameField = nz(surname).toUpperCase().replace(' ', '<')
                + "<<"
                + nz(givenNames).toUpperCase().replace(' ', '<');
        String line3 = padRight(nameField, 30, '<').substring(0, 30);

        return line1 + line2 + line3;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String sanitizeOpt(String s) {
        return (s == null || s.isEmpty()) ? "" : s.toUpperCase().replace(' ', '<');
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

    public byte[] buildDG11(String fullName,
                            String codiceFiscale,
                            LocalDate birthDate,
                            String placeOfBirth,
                            String address,
                            String telephone,
                            String profession) {

        BerTag T_DG11 = new BerTag(0x6B);
        BerTag T_TAGLIST = new BerTag(0x5C);
        BerTag T_FULLNAME = new BerTag(0x5F, 0x0E);
        BerTag T_PERSONAL_NUMBER = new BerTag(0x5F, 0x10);
        BerTag T_DOB = new BerTag(0x5F, 0x2B);
        BerTag T_POB = new BerTag(0x5F, 0x11);
        BerTag T_ADDR = new BerTag(0x5F, 0x42);
        BerTag T_TEL = new BerTag(0x5F, 0x12);
        BerTag T_PROF = new BerTag(0x5F, 0x13);

        List<byte[]> presentTags = new ArrayList<>();
        BerTlvBuilder inner = new BerTlvBuilder();

        if (fullName != null) {
            inner.addBytes(T_FULLNAME, fullName.getBytes(StandardCharsets.UTF_8));
            presentTags.add(new byte[]{(byte) 0x5F, (byte) 0x0E});
        }
        if (codiceFiscale != null) {
            inner.addBytes(T_PERSONAL_NUMBER, codiceFiscale.getBytes(StandardCharsets.UTF_8));
            presentTags.add(new byte[]{(byte) 0x5F, (byte) 0x10});
        }
        if (birthDate != null) {
            String yyyymmdd = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
            inner.addBytes(T_DOB, yyyymmdd.getBytes(StandardCharsets.US_ASCII));
            presentTags.add(new byte[]{(byte) 0x5F, (byte) 0x2B});
        }
        if (placeOfBirth != null) {
            inner.addBytes(T_POB, placeOfBirth.getBytes(StandardCharsets.UTF_8));
            presentTags.add(new byte[]{(byte) 0x5F, (byte) 0x11});
        }
        if (address != null) {
            inner.addBytes(T_ADDR, address.getBytes(StandardCharsets.UTF_8));
            presentTags.add(new byte[]{(byte) 0x5F, (byte) 0x42});
        }
        if (telephone != null) {
            inner.addBytes(T_TEL, telephone.getBytes(StandardCharsets.UTF_8));
            presentTags.add(new byte[]{(byte) 0x5F, (byte) 0x12});
        }
        if (profession != null) {
            inner.addBytes(T_PROF, profession.getBytes(StandardCharsets.UTF_8));
            presentTags.add(new byte[]{(byte) 0x5F, (byte) 0x13});
        }

        int total = presentTags.stream().mapToInt(t -> t.length).sum();
        byte[] tagListValue = new byte[total];
        int off = 0;
        for (byte[] t : presentTags) {
            System.arraycopy(t, 0, tagListValue, off, t.length);
            off += t.length;
        }

        BerTlvBuilder innerWithList = new BerTlvBuilder();
        innerWithList.addBytes(T_TAGLIST, tagListValue);

        BerTlvBuilder outer = new BerTlvBuilder();
        outer.addBytes(T_DG11, concat(innerWithList.buildArray(), inner.buildArray()));

        return outer.buildArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }


    private String padRight(String input, int length, char pad) {
        if (input.length() >= length) return input.substring(0, length);
        char[] padArray = new char[length - input.length()];
        Arrays.fill(padArray, pad);
        return input + new String(padArray);
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
