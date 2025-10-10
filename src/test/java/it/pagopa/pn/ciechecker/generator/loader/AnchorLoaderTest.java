package it.pagopa.pn.ciechecker.generator.loader;

import it.pagopa.pn.ciechecker.client.s3.S3BucketClient;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.CSCA_ANCHOR_PATH_FILENAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest(classes = { PnMandateConfig.class })
@TestPropertySource("classpath:application.properties")
class AnchorLoaderTest {

    @Autowired
    private PnMandateConfig config;
    @MockBean
    private S3BucketClient s3;

    private static final String MASTER_LIST_CSCA_ZIP_S_3 = "s3://dgs-temp-089813480515/IT_MasterListCSCA.zip";



    @BeforeEach
    void setUp() throws IOException {

        InputStream fileInputStream = new FileInputStream(Path.of(CSCA_ANCHOR_PATH_FILENAME).toFile());
        ResponseInputStream<GetObjectResponse> s3Stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(fileInputStream)
        );
        when(s3.getObjectContent(anyString()))
                .thenAnswer(invocation -> s3Stream);


    }

    @Test
    void loadFromS3Test() throws IOException {
        AnchorLoader loader = new AnchorLoader(config,s3);
        List<X509Certificate> certificates = loader.loadFromS3(MASTER_LIST_CSCA_ZIP_S_3);
        Assertions.assertNotNull(certificates);
        Assertions.assertTrue(certificates.size() > 0);
    }


}
