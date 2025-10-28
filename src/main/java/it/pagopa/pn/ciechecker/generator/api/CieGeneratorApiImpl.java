package it.pagopa.pn.ciechecker.generator.api;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.generator.challenge.ChallengeResponseBuilder;
import it.pagopa.pn.ciechecker.generator.constants.CieGeneratorConstants;
import it.pagopa.pn.ciechecker.generator.files.CieFilesExporter;
import it.pagopa.pn.ciechecker.generator.ias.IasBuilder;
import it.pagopa.pn.ciechecker.generator.loader.CertAndKeyLoader;
import it.pagopa.pn.ciechecker.generator.model.CertAndKey;
import it.pagopa.pn.ciechecker.generator.model.CieCaAndKey;
import it.pagopa.pn.ciechecker.generator.sod.SodMrtdBuilder;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.utils.LogsConstant;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;


@lombok.CustomLog
public class CieGeneratorApiImpl implements CieGeneratorApi {


    @Override
    public CieValidationData generateCieValidationData(Path outputDir,
                                                       String codiceFiscale,
                                                       LocalDate expirationDate,
                                                       String nonce) throws CieCheckerException {

        try {
            //recupero cert e key
            CertAndKey issuerCertAndKeyFromS3 = new CertAndKeyLoader(
            ).loadIssuerCertAndKeyFromS3();


            IasBuilder iasBuilder = new IasBuilder();
            // creazione ias
            CieIas ias = iasBuilder.createCieIas(
                    iasBuilder.generateNisNumericString(IasBuilder.DEFAULT_NIS_LEN).getBytes(),     //NIS
                    issuerCertAndKeyFromS3.getEncodedPublicKey(),                                   //PUBKEY
                    issuerCertAndKeyFromS3.getPrivateKey(),                                         //PRVKEY
                    issuerCertAndKeyFromS3.getCertificate()                                         //CERT
            );

            CieMrtd mrtd = new SodMrtdBuilder().buildCieMrtdAndSignSodWithDocumentSigner(
                    CieGeneratorConstants.DEFAULT_SURNAME,
                    CieGeneratorConstants.DEFAULT_GIVEN_NAME,
                    CieGeneratorConstants.DEFAULT_DOCUMENT_NUMBER,
                    CieGeneratorConstants.DEFAULT_NATIONALITY,
                    CieGeneratorConstants.DEFAULT_DATE_OF_BIRTH,
                    CieGeneratorConstants.DEFAULT_SEX,
                    CieGeneratorConstants.DEFAULT_EXPIRY_DATE,
                    codiceFiscale,
                    CieGeneratorConstants.DEFAULT_PLACE_OF_BIRTH,
                    issuerCertAndKeyFromS3.getPrivateKey(),
                    issuerCertAndKeyFromS3.getCertificate()
            );


            CieValidationData validationData = new CieValidationData();


            validationData.setCieMrtd(mrtd);
            validationData.setCieIas(ias);
            validationData.setNonce(nonce);
            validationData.setCodFiscDelegante(codiceFiscale);

            validationData.setSignedNonce(ChallengeResponseBuilder.signNonce(nonce,issuerCertAndKeyFromS3.getPrivateKey()));

            // CONVERT TO CIECAANDKEY
            CieCaAndKey cieCaAndkey = new CieCaAndKey();
            cieCaAndkey.setCertPem(issuerCertAndKeyFromS3.getEncodedCertificate());
            cieCaAndkey.setCertKey(issuerCertAndKeyFromS3.getEncodedPublicKey());
            //

            //EXPORT FILES
            final CieFilesExporter generator = new CieFilesExporter(validationData,cieCaAndkey,outputDir.toAbsolutePath().toString());
            final Set<String> exportedKeys = generator.exportCieArtifactsToFiles().keySet();
            exportedKeys.forEach(key ->
                log.debug("Exported file: {} ",key)
            );
            //
            return validationData;
        }catch (Exception e ){
            log.error(Exception.class + LogsConstant.MESSAGE  + e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

}
