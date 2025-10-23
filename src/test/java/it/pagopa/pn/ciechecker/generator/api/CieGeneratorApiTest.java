package it.pagopa.pn.ciechecker.generator.api;


import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.client.s3.S3BucketClient;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.CSCA_ANCHOR_PATH_FILENAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {CieGeneratorApi.class, S3Client.class, CieCheckerImpl.class})
@Slf4j
@ActiveProfiles("test")
@EnableConfigurationProperties(PnMandateConfig.class)
public class CieGeneratorApiTest {

    @SpyBean
    private PnMandateConfig config;

    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3BucketClient s3BucketClient;

    @Autowired
    private CieChecker cieChecker;

    private static final Path basePath = Path.of("src", "test", "resources");
    private static final Path outputDir = Path.of("src", "test", "resources","output-dir");

    private static final String CA_AND_KEY_ZIP="ca_and_key.zip";
    //PARAMETERS
    private static final String COD_FISCALE="RSSMRA80A01H501U";
    private static final String NONCE="02461";
    private static final LocalDate EXPIRY_DATE=  LocalDate.of(2030, 12, 31);
    public static final String NEW_CSCA_ANCHOR_PATH_FILENAME = "src/test/resources/new_IT_MasterListCSCA.zip";


    @BeforeEach
    void setUp() throws Exception {
        InputStream inputStream = new FileInputStream(Path.of(basePath.toString(), CA_AND_KEY_ZIP).toFile());
        ResponseInputStream<GetObjectResponse> s3Stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(inputStream)
        );
        when(s3Client.getObject(Mockito.any(GetObjectRequest.class))).thenAnswer(i ->s3Stream);

        InputStream fileInputStream = new FileInputStream(Path.of(NEW_CSCA_ANCHOR_PATH_FILENAME).toFile());
        ResponseInputStream<GetObjectResponse> s3BucketClientStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(fileInputStream)
        );
        when(s3BucketClient.getObjectContent(anyString()))
                .thenAnswer(invocation -> s3BucketClientStream);

        cieChecker.init();
    }
/*
    @Test
    public void cieValidationDataMandateTest(){

        CieGeneratorApiImpl cieGeneratorApi = new CieGeneratorApiImpl();

        CieValidationData data = cieGeneratorApi.generateCieValidationData(outputDir.toAbsolutePath(),COD_FISCALE,EXPIRY_DATE,NONCE);
        Assertions.assertEquals(cieChecker.validateMandate(data), ResultCieChecker.OK);
    }

 */


}
