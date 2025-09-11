package it.pagopa.pn.mandate.validate;


import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;

import it.pagopa.pn.ciechecker.model.SodSummary;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.operator.OperatorCreationException;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.X_509;
import static it.pagopa.pn.ciechecker.utils.ValidateUtils.decodeSodHr;
import static org.junit.jupiter.api.Assertions.*;

class ValidateUtilsTest {

    private static final Path BASE_PATH = Path.of("src","test","resources");
    private static final Path CSCA_DIR = Path.of("src","test","resources","csca");
    private static final String SOD_HEX = "SOD_IAS.HEX";
    private static final String EF_SOD_HEX = "EF_SOD.HEX";
    private static final String NIS_PUBKEY_FILENAME = "NIS_PUBKEY.HEX";
    private static final String NIS_HEX_TO_CHECK="393130373138343634363534";
    private static final Path sodFile = Paths.get("src/test/resources/EF.SOD");
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


    @Test
    void extractPublicKeyFromHolder() throws IOException, DecoderException, CMSException, CertificateException {
        System.out.println("TEST extractPublicKeyFromHolder - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        X509CertificateHolder x509Certificate = ValidateUtils.extractDscCertDer(sodIasByteArray);
        System.out.println(" - Estrae il certificato X509CertificateHolder: " + x509Certificate);
        System.out.println(" - Estrae il certificato X509CertificateHolder: " + x509Certificate.getSignatureAlgorithm().getAlgorithm());
        //Estrae il certificato X509CertificateHolder: 1.2.840.113549.1.1.5
        Assertions.assertNotNull(x509Certificate);

        System.out.println(" - Estrae la publicKey dal certificato X509CertificateHolder");
        PublicKey publicKey = ValidateUtils.extractPublicKeyFromHolder( x509Certificate); //SerialNumber: 1382935226051631925
        System.out.println(" - publicKey: " + publicKey);
        System.out.println(" - publicKey Format: " + publicKey.getFormat()); //publicKey Format: X.509
        System.out.println(" - publicKey Algorithm: " + publicKey.getAlgorithm()); //RSA
        byte[] certDer = publicKey.getEncoded();  //bytes del certificato DER usato per l’estrazione OpenSSL
        Assertions.assertNotNull(certDer);
        System.out.println("//////////////////");

        // AND: la public key PEM estratta con OpenSSL (BEGIN PUBLIC KEY ... END PUBLIC KEY)
        String openSslPem = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAytYrOy71s5KcL8FpSOwC
        MI/6+ZyaZkjMMbl/BDBtC59hlt8q5CptJihGqaRl5LeLJG7OqMfRteLtpHmsac5r
        ZmTUncm+mCPMKy1p8EDpYscHneyFGnbbSyH9xKt8QLHV/O8d96dGl/iYNsk7wF8R
        ihEy62qwfVUgeqhpaVNfEg1FYSOLLbR9OcBKRLamZcJrOqd5vuGNHZKyToqoWqhS
        ZntbKyZIC93ibnLiQkhetPnrZoCm1s81v8EW6ASbhpWaJEcv3xwe9nZxqjr9tMkO
        x9sOAT7gIN2hBQZasVxeCelfCZRjyh+P0j37DMpBaPCMlWLUeYQgKrd+aJtyTsnW
        /QIDAQAB
        -----END PUBLIC KEY-----
        """;

        // WHEN: confronto gli encoding SPKI DER
        byte[] spkiFromOpenSsl = decodePublicKeyPemToDer(openSslPem);
        byte[] spkiFromCert    = publicKey.getEncoded();

        // THEN: devono coincidere
        Assertions.assertArrayEquals(spkiFromOpenSsl, spkiFromCert, "SPKI DER must match");

    }

    private static byte[] decodePublicKeyPemToDer(String pem) {
        // Strip header/footer and whitespace, then Base64-decode
        String b64 = pem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }

    @Test
    void extractSignaturesFromSignedData() throws IOException, DecoderException, CMSException {
        System.out.println("TEST extractSignaturesFromSignedData - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);
        List<byte[]> signatures  = ValidateUtils.extractSignaturesFromSignedData(cms);

        Assertions.assertTrue(!signatures.isEmpty());
        System.out.println(" - signatures SIZE: " + signatures.size());
        //[104, 8, 11, -125, -86, -123, 111, -61, -81, 31, -102, 29, -119, 112, 17, 82, 97, 48, -82, -123, -30, 54, -122, 40, -112, -105, 13, 84, 116, 101, 53, -33, 51, 40, -83, 6, 56, -55, 5, -124, -80, -37, 10, 70, 108, -122, -7, -90, 80, 73, -5, -56, 48, -29, 68, -103, -3, 84, 16, 117, 54, 32, 77, -32, 41, -91, 117, -11, -39, 126, 31, 29, -1, 96, 75, 86, 10, -105, 70, 94, 43, 95, 16, 91, -3, -105, -80, -56, 110, -76, -110, -23, 96, -7, 41, -38, 41, -115, 111, -72, +156 more]
        System.out.println(" - signatures length: " + signatures.get(0).length); //256
        String signaturesToHex = ValidateUtils.bytesToHex(signatures.get(0));
        System.out.println("HEX signatures: " + signaturesToHex);

        String signaturesToVerify = "68080b83aa856fc3af1f9a1d897011526130ae85e236862890970d54746535df3328ad0638c90584b0db0a466c86f9a65049fbc830e34499fd54107536204de029a575f5d97e1f1dff604\n" +
                "b560a97465e2b5f105bfd97b0c86eb492e960f929da298d6fb87637051b37980c8ac4737fbfe3c37993a41fdc2749da2eb3b21b51a2fe66979d2258fb161f8bb1dfe8bcfbfc55e50e5d62f3f4fdcb2bf8\n" +
                "80b60d63ec411809c813cc2f9da24c03b6a45b15d3aecda1047fab8806029a54d70a4e9a4e3de888899d2a6762b073c91acbd2c39a27105cf6c24cf78352c115a18e97e9a2d29164177db03c6f3d90f03b7d842df31b0fe9a36ca361dd722b5afd01ea10bd";

        //Assertions.assertEquals(signaturesToHex, signaturesToVerify.replaceAll("\\s+", ""));
        Assertions.assertTrue(signaturesToHex.equalsIgnoreCase(signaturesToVerify.replaceAll("\\s+", "")));
    }

    @Test
    void extractFirstAndFiveHashesOctetString() throws IOException, DecoderException, CMSException, NoSuchAlgorithmException {

        System.out.println("TEST extractFirstAndFiveHashesOctetString - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);

        // --- PARTE 1: ESTRAI E CALCOLA L'HASH DEI DATI FIRMATI ---
        //la prima occorrenza : #a77dbdb693edd191ec82412c1462c70df7901cdca12088cd23caf74429681a86
        //contiene la concatenazione degli hash dei dati originali ossia i file che sono stati firmati - hashesBlock
        //ASN1OctetString firstOctetString = impl.extractHashesOctetString(cms, 1);
        byte[] dataToHash = ValidateUtils.extractHashBlock(cms);
        System.out.println("PRIMA OCCORRENZA DI octetString: " + dataToHash.toString());
        Assertions.assertNotNull(dataToHash);

        // --- PARTE 2: ESTRAI L'HASH FIRMATO (messageDigest) ---
        SignerInformationStore signers = cms.getSignerInfos();
        if (signers.size() == 0) {
            System.err.println("Nessun firmatario trovato.");
            return;
        }
        ASN1OctetString newFive = ValidateUtils.extractHashSigned(cms);
        //getHexFromOctetString - fiveStr: C9002855CB7A5D366DB2CD6CCD6E148B7F8265E765C520ACC88855C2F3338FEB
        System.out.println("QUINTA OCCORRENZA DI octetString: " + newFive.toString());
        Assertions.assertTrue(ValidateUtils.verifyOctetStrings(dataToHash, newFive));
    }

    @Test
    void extractSignedAttributes() throws IOException, DecoderException, CMSException {
        System.out.println("TEST extractSignedAttributes - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);

        Hashtable<ASN1ObjectIdentifier, Attribute> attributes = ValidateUtils.extractAllSignedAttributes(cms);

        //chiave (OID) della Hashtable
        Enumeration<ASN1ObjectIdentifier> oids = attributes.keys();
        while (oids.hasMoreElements()) {
            ASN1ObjectIdentifier oid = oids.nextElement();
            System.out.println("  - Attributo (OID): " + oid.getId());
            // Ottieni l'oggetto Attribute
            Attribute attribute = attributes.get(oid);
            // Stampa i valori dell'attributo, che sono contenuti in un ASN1Set
            System.out.println("    Valori:");
            attribute.getAttrValues().forEach(value -> {
                System.out.println("    - " + value.toString());
            });
        }
/*
    signedAttributes.size: 2
    signedAttributes.size: {1.2.840.113549.1.9.3=org.bouncycastle.asn1.cms.Attribute@45079570, 1.2.840.113549.1.9.4=org.bouncycastle.asn1.cms.Attribute@d6ceaeb2}
    - Attributo (OID): 1.2.840.113549.1.9.3
        Valori:
            - 2.23.136.1.1.1
    - Attributo (OID): 1.2.840.113549.1.9.4
        Valori:
            - #c9002855cb7a5d366db2cd6ccd6e148b7f8265e765c520acc88855c2f3338feb
 */
        Assertions.assertNotNull(attributes);

    }

    /*
     * PASSO 1B: ANALISI DEGLI HASH DEI DATI (DataGroupHashes)
     */

    //Verifica specifica dell'hash del NIS...
    @Test
    void extractDataGroupHashesForSpecifyNIS() throws IOException, DecoderException, CMSException, NoSuchAlgorithmException {
        System.out.println("TEST extractDataGroupHashes - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);

        List<String> dataGroupList = ValidateUtils.extractDataGroupHashes(cms);
        // dataGroupList ha 6 elementi
        Assertions.assertNotNull(dataGroupList);

        //Verifica specifica dell'hash del NIS...
        String nisSha256String = decodeNisSha256String();
        Assertions.assertTrue(dataGroupList.contains(nisSha256String));
    }

    String decodeNisSha256String() throws DecoderException, NoSuchAlgorithmException {
        byte[] nisHexToCheck = hexFile(NIS_HEX_TO_CHECK);
        System.out.println("DECODED_NIS_STRING : " + nisHexToCheck);

        String nisSha256Str = ValidateUtils.calculateSha256(nisHexToCheck);
        System.out.println("nisHexToCheckStr : " + nisSha256Str);
        // E0C47E69639807307D6DB3EE8E3C4E5893B6093E2F04397E140BA26F29C54663
        return nisSha256Str;
    }

    //Verifica specifica dell'hash della chiave pubblica... ????
    //hash del primo "Data Group" che si trova all'interno del file SOD_IAS.HEX, e non l'hash della chiave pubblica.
    @Test
    void extractDataGroupHashesForPUBKEY() throws IOException, DecoderException, CMSException, NoSuchAlgorithmException {
        System.out.println("TEST extractDataGroupHashes - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);

        List<String> dataGroupList = ValidateUtils.extractDataGroupHashes(cms);
        // dataGroupList ha 6 elementi
        Assertions.assertNotNull(dataGroupList);

        //Verifica specifica dell'hash della chiave pubblica...
        String nisPublicKeyString = decodeNisPublicKeyTest();
        Assertions.assertTrue(dataGroupList.contains(nisPublicKeyString));
    }

    String decodeNisPublicKeyTest() throws NoSuchAlgorithmException, IOException, DecoderException {

        String fileString = Files.readString(BASE_PATH.resolve(NIS_PUBKEY_FILENAME)).replaceAll("\\s+", "");
        System.out.println("fileString : " + fileString);

        byte[] nisPublicKeyToCheck = hexFile(fileString);
        System.out.println("DECODED_NIS_PUBLICKEY_STRING : " + nisPublicKeyToCheck);

        String nisSha256PublicKeyStr = ValidateUtils.calculateSha256(nisPublicKeyToCheck);
        System.out.println("nisSha256PublicKeyStr : " + nisSha256PublicKeyStr);
        // A77DBDB693EDD191EC82412C1462C70DF7901CDCA12088CD23CAF74429681A86
        Assertions.assertNotNull(nisSha256PublicKeyStr);
        return nisSha256PublicKeyStr;
    }

/// ///

/*
    @Test
    void veryfySignedAttrIsSet() throws IOException, DecoderException, CMSException {
        System.out.println("TEST convertSignedAttributesIntoSet - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        //System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);

        Assertions.assertTrue(ValidateUtils.veryfySignedAttrIsSet ( cms));
    }

    @Test
    void veryfySignatures() throws IOException, DecoderException, CMSException {
        System.out.println("TEST convertSignedAttributesIntoSet - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        //System.out.println(" - sodIasByteArray: " + sodIasByteArray);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);

        Assertions.assertTrue(ValidateUtils.veryfySignatures( cms));

    }
    */

    @Test
    void verifyDigitalSignature() throws IOException, DecoderException, CMSException, CertificateException, OperatorCreationException {
        System.out.println("TEST convertSignedAttributesIntoSet - INIT");
        System.out.println(" - Leggo il file SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        CMSSignedData cms = new CMSSignedData(sodIasByteArray);

        Assertions.assertTrue(ValidateUtils.verifyDigitalSignature(cms));
    }

    @Test
    void testDecodeSodHrOk() throws Exception {
        byte[] sodBytes = Files.readAllBytes(sodFile);
        System.out.println("bytes letti dal file SOD: " + sodBytes.length);

        SodSummary summary = decodeSodHr(sodBytes);
        Assertions.assertNotNull(summary);
        Assertions.assertNotNull(summary.getDgDigestAlgorithm());
        Assertions.assertNotNull(summary.getDgExpectedHashes());
        assertFalse(summary.getDgExpectedHashes().isEmpty());
        Assertions.assertNotNull(summary.getSignatureAlgorithm());
        Assertions.assertNotNull(summary.getCmsSignature());
        Assertions.assertNotNull(summary.getDscCertificate());
        System.out.println("SodSummary: "+ summary);

    }
    @Test
    void testDecodeSodHrFail() {
        byte[] corruptedSod = new byte[]{0x01, 0x02, 0x03}; // SOD corrotto
        System.out.println("bytes letti dal file SOD corrotto: " + corruptedSod.length);

        Exception exception = assertThrows(Exception.class, () -> {
            ValidateUtils.decodeSodHr(corruptedSod);
        });
        System.out.println("Eccezione catturata: " + exception.getClass().getSimpleName() + " - " + exception.getMessage());
        assertTrue(exception.getMessage().contains("unable") || !exception.getMessage().isEmpty());
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


    @Test
    void verifyDigitalSignatureMrtd() throws Exception {
        System.out.println("TEST verifyDigitalSignatureMrtd - INIT");
        System.out.println(" - Leggo il file EF_SOD_HEX e decodifico in byte[] HEX");
        String fileString = Files.readString(BASE_PATH.resolve(EF_SOD_HEX)).replaceAll("\\s+", "");
        String subString = fileString.substring(8,fileString.length());
        byte[] efSod = hexFile(subString);

        CieMrtd cieMrtd = new CieMrtd();
        cieMrtd.setSod(efSod);

        List<byte[]> ders = pickManyDerFromResources(-1);
        String concatenatedPem = ders.stream()
                .map(d->new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
        byte[] blob = concatenatedPem.getBytes(StandardCharsets.UTF_8);
        cieMrtd.setCscaAnchor(List.of(blob));

        CieCheckerImpl cieChecker = new CieCheckerImpl();
        cieChecker.init();

        Assertions.assertTrue(cieChecker.verifyDigitalSignatureMrtd(cieMrtd));

    }
}
