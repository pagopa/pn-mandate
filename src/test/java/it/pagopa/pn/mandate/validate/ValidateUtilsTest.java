package it.pagopa.pn.mandate.validate;

import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

class ValidateUtilsTest {

    private static final Path basePath= Path.of("src","test","resources");
    private static final String SOD_IAS_FILENAME= "SOD_IAS.HEX";
    private static final Map<String,String> expectedIssuer = Map.of(
            "2.5.4.3", "Italian Country Signer CA",
            "2.5.4.11", "National Electronic Center of State Police",
            "2.5.4.10", "Ministry of Interior",
            "2.5.4.6", "IT"
    );

    @Test
    void extractDscCertDerTest() throws IOException, DecoderException, CMSException {

        String fileString = Files.readString(basePath.resolve(SOD_IAS_FILENAME)).replaceAll("\\s+", "");
        String subString = fileString.substring(8,fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        X509CertificateHolder x509Certificate = ValidateUtils.extractDscCertDer(sodIasByteArray);

        Assertions.assertNotNull(x509Certificate);

      Arrays.stream(x509Certificate.getIssuer().getRDNs()).forEach(elem ->{
           System.out.println(elem.getFirst().getType().toString());
           System.out.println(elem.getFirst().getValue().toString());
       });

        ASN1ObjectIdentifier objectIdentifier = new ASN1ObjectIdentifier("2.5.4.3");
        Arrays.stream(x509Certificate.getIssuer().getRDNs(objectIdentifier)).allMatch(elem ->{
            String key = elem.getFirst().getType().toString();
            Assertions.assertEquals(expectedIssuer.get(key), elem.getFirst().getValue().toString());
            return true;
        });
    }

    private static byte[] hexFile(String toHex) throws DecoderException {
        return Hex.decodeHex(toHex);
    }

}
