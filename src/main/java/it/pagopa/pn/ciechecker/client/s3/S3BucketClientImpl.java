package it.pagopa.pn.ciechecker.client.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.utils.LogsCostant;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.PROTOCOLLO_S3;

@Service
@AllArgsConstructor
@Slf4j
@Data
public class S3BucketClientImpl  implements S3BucketClient {


    private static final DefaultCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER_V2 = DefaultCredentialsProvider.create();

    private final PnMandateConfig pnMandateConfig;

    private S3Client clientS3;

    @Bean
    //@ConditionalOnProperty(name = "aws.use-s3", havingValue = "true", matchIfMissing = true)
    public S3Client s3Client() {
        log.info("pnMandateConfig.s3Client() {}", pnMandateConfig.getProfileName());
        S3ClientBuilder clientBuilder = pnMandateConfig.configureBuilder( S3Client.builder() );

        return clientBuilder.build();
    }

    public InputStream getFileInputStreamCscaAnchor(String cscaAnchorPathFileName) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.S3BUCKETCLIENTIMPL_EXTRACT_CSCAANCHOR);
        if(Objects.isNull(cscaAnchorPathFileName) || cscaAnchorPathFileName.isBlank()) {
            log.debug("la variabile 'pn.mandate.ciechecker.csca-anchor.pathFileName' nel property file IS NULL o BLANK");
            cscaAnchorPathFileName =  "s3://dgs-temp-089813480515/IT_MasterListCSCA.zip";
        }
        log.debug("Variable 'pn.mandate.ciechecker.csca-anchor.pathFileName': {}",cscaAnchorPathFileName);

        try {
            if (cscaAnchorPathFileName.startsWith(PROTOCOLLO_S3) ){
                return getObjectContent(cscaAnchorPathFileName);
            }else{
                return new FileInputStream(Path.of(cscaAnchorPathFileName).toFile());
            }

        }catch (IOException ioe) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.S3BUCKETCLIENTIMPL_EXTRACT_CSCAANCHOR, ioe.getClass().getName() + " - Message: " + ioe.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED, ioe);
        }
    }



    @Override
    public InputStream getObjectContent(String s3Uri) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.S3BUCKETCLIENTIMPL_GET_OBJECT_CONTENT, "Call s3 bucket for read content object with s3Uri: {}", s3Uri);
        String[] s3UriInfo = extractS3Components( s3Uri);

        if(Objects.isNull(s3UriInfo)) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.S3BUCKETCLIENTIMPL_GET_OBJECT_CONTENT, ResultCieChecker.KO_EXC_NOVALID_URI_CSCA_ANCHORS.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOVALID_URI_CSCA_ANCHORS);
        }
//        com.amazonaws.services.s3.AmazonS3 s3 = com.amazonaws.services.s3.AmazonS3ClientBuilder.standard().setCredentials(DEFAULT_CREDENTIALS_PROVIDER_V2.resolveCredentials()).withRegion(Regions.DEFAULT_REGION).build();
//        AmazonS3 clientS3 = AmazonS3ClientBuilder.standard()
//                .withCredentials(new DefaultAWSCredentialsProviderChain())
//                .withRegion(Regions.EU_SOUTH_1) // oppure la tua regione
//                .build();



        try {
            clientS3 =  s3Client();
            //S3Object o = clientS3.getObject(s3UriInfo[0], s3UriInfo[1]);
            ResponseInputStream o = clientS3.getObject(GetObjectRequest.builder().bucket(s3UriInfo[0]).key(s3UriInfo[1]).build());
//            clientS3 = pnMandateConfig.s3Client();
//
//            ResponseInputStream inputStream = clientS3.getObject(GetObjectRequest.builder().bucket(s3UriInfo[0]).key(s3UriInfo[1]).build());
//                    //s3UriInfo[0], s3UriInfo[1]);
//            return null;
                return o;
                //return o.getObjectContent();
        }catch (Exception e ){
            log.error("ERRORE: getObjectContent ");
            e.printStackTrace();
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }


    public String[] extractS3Components(String s3Uri) {

        //Verifica e rimuovi il prefisso "s3://"
        if (s3Uri == null || s3Uri.trim().isEmpty() || !s3Uri.startsWith(PROTOCOLLO_S3)) {
            log.error("Error: L'URI S3 is not valid o not begin with 's3://'");
            return null;
        }
        try {
            // Creiamo un oggetto URI
            URI uri = new URI(s3Uri);

            // Il nome del bucket è l'host/autorità dell'URI S3
            String bucketName = uri.getHost();

            // La chiave dell'oggetto è il percorso dell'URI (path)
            //    Questo include lo '/' iniziale, che va rimosso.
            String objectKey = uri.getPath();
            if (objectKey != null && objectKey.startsWith("/")) {
                objectKey = objectKey.substring(1);
            }

            log.debug("URI di Input: " + s3Uri);
            log.debug("-------------------------------------");
            log.debug("Bucket estratto:  " + bucketName );
            log.debug("Chiave estratta: " + objectKey );

            return new String[]{bucketName, objectKey};

        } catch (URISyntaxException e) {
            log.error("Sintax error in URI S3: {}" , e.getMessage());
            return null;
        }
    }


}
