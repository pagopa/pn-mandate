package it.pagopa.pn.ciechecker.generator.ias;


import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerConstants;
import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.CieCheckerInterface;
import it.pagopa.pn.ciechecker.client.s3.S3BucketClient;
import it.pagopa.pn.ciechecker.generator.model.CaAndKey;
import it.pagopa.pn.ciechecker.generator.model.CertAndKey;
import it.pagopa.pn.ciechecker.generator.model.Issuer;
import it.pagopa.pn.ciechecker.generator.pki.CiePki;
import it.pagopa.pn.ciechecker.model.*;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import static it.pagopa.pn.ciechecker.CieCheckerConstants.OK;
import static it.pagopa.pn.ciechecker.generator.ias.NisBuilder.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {it.pagopa.pn.ciechecker.CieCheckerImpl.class, it.pagopa.pn.ciechecker.client.s3.S3BucketClientImpl.class})
@ActiveProfiles("test")
@EnableConfigurationProperties(PnMandateConfig.class)
public class NisBuilderTest {
    private static NisBuilder nisBuilder;
    private static final Path basePath = Paths.get("src/test/resources");
    private static byte[] caCertBytes;
    private static byte[] caKeyBytes;
    @Autowired
    private CieChecker cieChecker;
    @Autowired
    private CieCheckerInterface cieCheckerInterface;
    @MockBean
    private S3BucketClient s3BucketClient;


    @BeforeEach
    void setUp() throws IOException {
        caCertBytes = Files.readAllBytes(basePath.resolve("catest.pem"));
        caKeyBytes  = Files.readAllBytes(basePath.resolve("catest.key"));

        byte[] masterListBytes = Files.readAllBytes(basePath.resolve("new_IT_MasterListCSCA.zip"));

        // mock del client S3: ogni volta restituisce un nuovo stream dai byte in memoria (evita che venga consumata la risposta prima di altre operazioni)
        when(s3BucketClient.getObjectContent(anyString()))
                .thenAnswer(invocation -> {
                    ByteArrayInputStream bais = new ByteArrayInputStream(masterListBytes);
                    return new ResponseInputStream<>(
                            GetObjectResponse.builder().build(),
                            AbortableInputStream.create(bais)
                    );
                });

        cieChecker.init();
        List<X509Certificate> cscaCerts = cieCheckerInterface.getCscaAnchor();
        for (X509Certificate cert : cscaCerts) {
            System.out.println("BEFORE EACH: "+cert.getSubjectX500Principal());
        }
    }




    @Test
    public void generateRandom_defaultLen_valid() {
        NisBuilder nb = new NisBuilder(new SecureRandom());
        byte[] a = nb.generateRandom();
        byte[] b = nb.generateRandom();

        assertEquals(NisBuilder.DEFAULT_NIS_LEN, a.length);
        assertEquals(NisBuilder.DEFAULT_NIS_LEN, b.length);
        assertTrue(NisBuilder.isValid(a));
        assertTrue(NisBuilder.isValid(b));
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    public void generateFromSeed_isDeterministic() {
        NisBuilder nb = new NisBuilder();
        byte[] s1 = nb.generateFromSeed("RSSMRA80A01H501U", "AA1234567");
        byte[] s2 = nb.generateFromSeed("rssmra80a01h501u", "  aa1234567  "); // normalizzazione
        assertArrayEquals(s1, s2);
    }

    @Test
    public void customLength_bounds() {
        NisBuilder nb = new NisBuilder();
        assertThrows(IllegalArgumentException.class, () -> nb.generateRandom(0));
        assertThrows(IllegalArgumentException.class, () -> nb.generateRandom(128));
        assertDoesNotThrow(() -> nb.generateRandom(32));
    }

    @Test
    public void asReadOnlyBuffer_works() {
        NisBuilder nb = new NisBuilder();
        byte[] nis = nb.generateRandom();
        ByteBuffer ro = NisBuilder.asReadOnlyBuffer(nis);
        assertEquals(nis.length, ro.remaining());
        assertTrue(ro.isReadOnly());
    }

    @Test
    public void testBuildIasSodHashesOnly() throws Exception {
        PrivateKey caPrivateKey = loadPrivateKeyFromPem(caKeyBytes);
        X509Certificate caCert = loadCertificateFromPem(caCertBytes);

        byte[] nis = new NisBuilder().generateRandom();
        CieIas cieIas = createCieIas(nis, caCert.getPublicKey().getEncoded(), caPrivateKey, caCert);
        byte[] sodBytes = cieIas.getSod();


        assertNotNull(cieIas.getSod());
        assertNotNull(cieIas.getNis());
        assertNotNull(cieIas.getPublicKey());
        assertTrue(cieIas.getSod().length > 0);

        System.out.println("CIEIAS SOD L: "+ cieIas.getSod().length);
        System.out.println("CIEIAS NIS L: "+ cieIas.getNis().length);
        System.out.println("CIEIAS pk: "+ cieIas.getPublicKey().length);



        System.out.println("SOD IAS generato correttamente (" + cieIas.getSod().length + " bytes)");

    }

    //creato per verifica locale sul sod
    @Test
    public void generateSodFileInTestResources() throws Exception {
        PrivateKey caPrivateKey = loadPrivateKeyFromPem(caKeyBytes);
        X509Certificate caCert = loadCertificateFromPem(caCertBytes);

        byte[] nis = new NisBuilder().generateRandom(); // genera NIS 32 byte
        CieIas cieIas = createCieIas(nis, caCert.getPublicKey().getEncoded(), caPrivateKey, caCert);
        byte[] sodBytes = cieIas.getSod();

        Path output = Path.of("src/test/resources/generetedFiles/generated_test.sod");
        File file = output.toFile();
        file.getParentFile().mkdirs(); 
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(sodBytes);
        }

        System.out.println("SOD generato in: " + file.getAbsolutePath());
        CMSSignedData cms = new CMSSignedData(sodBytes);
        assertNotNull(cms);
        assertFalse(cms.getSignerInfos().getSigners().isEmpty(), "CMS non contiene firmatari");
    }

//
//    @Test
//    public void testVerifySodPassiveAuthCie() throws Exception {
//        // ca reale
//        PrivateKey caPrivateKey = loadPrivateKeyFromPem(caKeyBytes);
//        X509Certificate caCert = loadCertificateFromPem(caCertBytes);
//
//        // nis random
//        byte[] nis = new NisBuilder().generateRandom();
//        CieIas cieIas = createCieIas(nis,caCert.getPublicKey().getEncoded(),caPrivateKey,caCert);
//
//        // cms sod ias
//        CMSSignedData cms = new CMSSignedData(cieIas.getSod());
//        System.out.println("getName: " +caCert.getSubjectX500Principal().getName());
//
//        assertNotNull(cms);
//        assertFalse(cms.getSignerInfos().getSigners().isEmpty(), "CMS non contiene firmatari");
//
//        // verifica firma digitale e catena
//        ResultCieChecker result = cieCheckerInterface.verifyDigitalSignature(cms);
//        assertEquals(OK, result.getValue());
//
//        // verifica hash NIS + firma
//       assertTrue(cieCheckerInterface.verifySodPassiveAuthCie(cms, cieIas.getNis()));
//    }



    public static PrivateKey loadPrivateKeyFromPem(byte[] pemBytes) throws Exception {
        try (PemReader reader = new PemReader(new InputStreamReader(new ByteArrayInputStream(pemBytes)))) {
            PemObject pemObject = reader.readPemObject();
            if (pemObject == null || !pemObject.getType().contains("PRIVATE KEY")) {
                throw new IllegalArgumentException("PEM does not contain a PRIVATE KEY");
            }

            byte[] keyBytes = pemObject.getContent();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        }
    }

    public static X509Certificate loadCertificateFromPem(byte[] pemBytes) throws Exception {
        try (PemReader reader = new PemReader(new InputStreamReader(new ByteArrayInputStream(pemBytes)))) {
            PemObject pemObject = reader.readPemObject();
            if (pemObject == null || !pemObject.getType().contains("CERTIFICATE")) {
                throw new IllegalArgumentException("PEM does not contain a CERTIFICATE");
            }
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(pemObject.getContent()));
        }
    }

}
