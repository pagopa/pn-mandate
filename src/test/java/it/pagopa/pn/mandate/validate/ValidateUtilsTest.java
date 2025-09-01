package it.pagopa.pn.mandate.validate;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.X_509;

class ValidateUtilsTest {

    private static final Path BASE_PATH = Path.of("src","test","resources");
    private static final Path CSCA_DIR = Path.of("src","test","resources","csca");
    private static final String SOD_HEX = "SOD_IAS.HEX";
    private static final Map<String,String> expectedIssuer = Map.of(
            "2.5.4.3", "Italian Country Signer CA",
            "2.5.4.11", "National Electronic Center of State Police",
            "2.5.4.10", "Ministry of Interior",
            "2.5.4.6", "IT"
    );

    // extractDscCertDer

    @Test
    void extractDscCertDerTest() throws IOException, DecoderException,CMSException {

        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(extractCertificateByteArray());

        Assertions.assertNotNull(holder);

      Arrays.stream(holder.getIssuer().getRDNs()).forEach(elem ->{
           System.out.println(elem.getFirst().getType().toString());
           System.out.println(elem.getFirst().getValue().toString());
       });

        ASN1ObjectIdentifier objectIdentifier = new ASN1ObjectIdentifier("2.5.4.3");
        Arrays.stream(holder.getIssuer().getRDNs(org.bouncycastle.asn1.ASN1ObjectIdentifier.getInstance(objectIdentifier))).allMatch(elem ->{
            String key = elem.getFirst().getType().toString();
            Assertions.assertEquals(expectedIssuer.get(key), elem.getFirst().getValue().toString());
            return true;
        });
    }

    // verifyDscAgainstTrustBundle

    @Test
    void verifyDscAgainstTrustBundleTest() throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(X_509);
        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(extractCertificateByteArray());
        List<byte[]> ders = pickManyDerFromResources(-1);

        String concatenatedPem = ders.stream()
                .map(d->new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());

        byte[] blob = concatenatedPem.getBytes(StandardCharsets.UTF_8);

        Collection<X509Certificate> anchors = ValidateUtils.parseCscaAnchors(List.of(blob),certificateFactory);

        Assertions.assertTrue(ValidateUtils.verifyDscAgainstTrustBundle(holder,anchors,null));
    }

    @Test
    void verifyDscAgainstTrustBundleIsFalseTest() throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(X_509);

        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(extractCertificateByteArray());
        byte[] dscDer = holder.getEncoded();
        List<byte[]> ders = pickManyDerFromResources(-1);

        String concatenatedPem = ders.stream()
                .map(d->new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());

        byte[] blob = concatenatedPem.getBytes(StandardCharsets.UTF_8);

        Collection<X509Certificate> anchors = ValidateUtils.parseCscaAnchors(List.of(blob),certificateFactory);

        X509Certificate dscX509 = (X509Certificate) CertificateFactory.getInstance(X_509)
                .generateCertificate(new ByteArrayInputStream(dscDer));

        List<X509Certificate> wrongBundle = anchors.stream()
                .filter(ca -> {
                    try {
                        dscX509.verify(ca.getPublicKey());
                        return false;
                    } catch (Exception e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());

        Assertions.assertFalse(ValidateUtils.verifyDscAgainstTrustBundle(dscDer, wrongBundle, null));
    }

    @Test
    void verifyDscAgainstAnchorBytes_derDsc_pemBundle_true() throws Exception {

        byte[] pkcs7 = extractCertificateByteArray();
        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(pkcs7);
        byte[] dscDer = holder.getEncoded();

        List<byte[]> ders = pickManyDerFromResources(-1);
        String concatenatedPem = ders.stream()
                .map(d -> new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
        byte[] caBlob = concatenatedPem.getBytes(StandardCharsets.UTF_8);

        Assertions.assertTrue(
                ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(caBlob), null)
        );
    }

    @Test
    void verifyDscAgainstAnchorBytes_pemDsc_pemBundle_true() throws Exception {
        byte[] pkcs7 = extractCertificateByteArray();
        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(pkcs7);
        byte[] dscPem = toPem(holder.getEncoded());

        List<byte[]> ders = pickManyDerFromResources(-1);
        String concatenatedPem = ders.stream()
                .map(d -> new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
        byte[] caBlob = concatenatedPem.getBytes(StandardCharsets.UTF_8);

        Assertions.assertTrue(
                ValidateUtils.verifyDscAgainstAnchorBytes(dscPem, List.of(caBlob), null)
        );
    }

    @Test
    void verifyDscAgainstAnchorBytes_false_when_all_parents_removed() throws Exception {
        var cf = CertificateFactory.getInstance(X_509);

        // DSC (DER)
        byte[] pkcs7 = extractCertificateByteArray();
        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(pkcs7);
        byte[] dscDer = holder.getEncoded();
        X509Certificate dscX509 = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(dscDer));

        // Anchors
        List<byte[]> anchorsDer = pickManyDerFromResources(-1);

        // filtering all valid anchors
        List<byte[]> wrongAnchorBlobs = new ArrayList<>();
        int removed = 0;
        for (byte[] der : anchorsDer) {
            X509Certificate ca = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            boolean verifies;
            try {
                dscX509.verify(ca.getPublicKey());
                verifies = true;
            } catch (Exception e) {
                verifies = false;
            }
            if (!verifies) wrongAnchorBlobs.add(der); else removed++;
        }
        Assertions.assertTrue(removed > 0);

        Assertions.assertFalse(
                ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, wrongAnchorBlobs, null)
        );
    }

    @Test
    void verifyDscAgainstAnchorBytes_edgeCases() throws Exception {
        List<byte[]> ders = pickManyDerFromResources(-1);
        String concatenatedPem = ders.stream()
                .map(d -> new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
        byte[] caBlob = concatenatedPem.getBytes(StandardCharsets.UTF_8);

        Assertions.assertFalse(ValidateUtils.verifyDscAgainstAnchorBytes(null, List.of(caBlob), null));

        // anchors null
        byte[] pkcs7 = extractCertificateByteArray();
        byte[] dscDer = ValidateUtils.extractDscCertDer(pkcs7).getEncoded();
        Assertions.assertFalse(ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, null, null));

        // anchors empty
        Assertions.assertFalse(ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(), null));

        // anchor malformed
        byte[] malformed = "INVALID".getBytes(StandardCharsets.UTF_8);
        Assertions.assertFalse(ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(malformed), null));
    }

    @Test
    void verify_invalid_inputs() {
        // dsc non-parsable
        Assertions.assertThrows(CieCheckerException.class,
                () -> ValidateUtils.verifyDscAgainstTrustBundle(new byte[]{0x01,0x02}, List.of(), null));

        // dsc len 0 ⇒ false
        Assertions.assertFalse(ValidateUtils.verifyDscAgainstTrustBundle(new byte[0], List.of(), null));

        // anchors null ⇒ false
        Assertions.assertFalse(ValidateUtils.verifyDscAgainstTrustBundle(new byte[]{1}, null, null));
    }

    // parseCscaAnchors

    @Test
    void parseCscaAnchorsTest() throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(X_509);
        List<byte[]> ders = pickManyDerFromResources(-1);

        String concatenatedPem = ders.stream()
                .map(d->new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());

        byte[] blob = concatenatedPem.getBytes(StandardCharsets.UTF_8);

        List<X509Certificate> anchors = ValidateUtils.parseCscaAnchors(List.of(blob),certificateFactory);

        Assertions.assertEquals(ders.size(), anchors.size());
    }

    private byte[] extractCertificateByteArray() throws DecoderException, IOException {
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8,fileString.length());
        return hexFile(subString);
    }

    private static byte[] hexFile(String toHex) throws DecoderException {
        return Hex.decodeHex(toHex);
    }

    private static List<byte[]> pickManyDerFromResources(int n) throws Exception {
        List<Path> ders = listDerFiles();
        Assertions.assertTrue(ders.size() >= n);
        List<byte[]> out = new ArrayList<>();
        if ( n == -1 ){
            for (Path der : ders) {
                out.add(Files.readAllBytes(der));
            }
        } else {
            for (int i = 0; i < n; i++) out.add(Files.readAllBytes(ders.get(i)));
        }
        return out;
    }

    private static byte[] toPem(byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String pem = "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
        return pem.getBytes(StandardCharsets.US_ASCII);
    }

    private static List<Path> listDerFiles() throws Exception {
        try (var s = Files.list(CSCA_DIR)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cer"))
                   .collect(Collectors.toList());

        }
    }

}
