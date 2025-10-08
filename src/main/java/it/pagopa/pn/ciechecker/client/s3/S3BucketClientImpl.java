package it.pagopa.pn.ciechecker.client.s3;


import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.utils.LogsCostant;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.Data;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.PROTOCOLLO_S3;

@Service
@AllArgsConstructor
@Slf4j
@Data
public class S3BucketClientImpl  implements S3BucketClient {


    private static final DefaultCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER_V2 = DefaultCredentialsProvider.create();

    private final PnMandateConfig pnMandateConfig;

    private final S3Client clientS3;


    @Override
    public InputStream getObjectContent(String s3Uri) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.S3BUCKETCLIENTIMPL_GET_OBJECT_CONTENT, "Call s3 bucket for read content object with s3Uri: {}", s3Uri);
        String[] s3UriInfo = extractS3Components( s3Uri);

        if(Objects.isNull(s3UriInfo)) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.S3BUCKETCLIENTIMPL_GET_OBJECT_CONTENT, ResultCieChecker.KO_EXC_NOVALID_URI_CSCA_ANCHORS.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOVALID_URI_CSCA_ANCHORS);
        }
        try {
            return clientS3.getObject(GetObjectRequest.builder().bucket(s3UriInfo[0]).key(s3UriInfo[1]).build());

        } catch (Exception e){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.S3BUCKETCLIENTIMPL_GET_OBJECT_CONTENT, e.getClass().getName()+" Message: " +e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOVALID_CONNECT_S3,e);
        }
    }


    private String[] extractS3Components(String s3Uri) {

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
