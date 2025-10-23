package it.pagopa.pn.ciechecker.generator.api;

import it.pagopa.pn.ciechecker.generator.challenge.ChallengeResponseBuilder;
import it.pagopa.pn.ciechecker.generator.constants.CieGeneratorConstants;
import it.pagopa.pn.ciechecker.generator.files.CieFileGenerator;
import it.pagopa.pn.ciechecker.generator.ias.NisBuilder;
import it.pagopa.pn.ciechecker.generator.loader.CertAndKeyLoader;
import it.pagopa.pn.ciechecker.generator.model.CertAndKey;
import it.pagopa.pn.ciechecker.generator.model.CieCaAndkey;
import it.pagopa.pn.ciechecker.generator.sod.SodMrtdBuilder;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;
import java.time.LocalDate;


@Slf4j
public class CieGeneratorApiImpl implements CieGeneratorApi {

    public CieGeneratorApiImpl() {
    }

    @Override
    public CieValidationData generateCieValidationData(Path outputDir,
                                                       String codiceFiscale,
                                                       LocalDate expirationDate,
                                                       String nonce) {

        try {
            //recupero cert e key
            CertAndKey caCertAndKey = new CertAndKeyLoader(
            ).loadCaAndKeyFromS3();


            NisBuilder iasBuilder = new NisBuilder();
            // creazione ias
            CieIas ias = iasBuilder.createCieIas(
                    iasBuilder.generateNumeric(NisBuilder.DEFAULT_NIS_LEN).getBytes(),  //NIS
                    caCertAndKey.keyPair().getPublic().getEncoded(),                      //PUBKEY
                    caCertAndKey.keyPair().getPrivate(),                                  //PRVKEY
                    caCertAndKey.certificate()                                            //CERT
            );

            CieMrtd mrtd = new SodMrtdBuilder().buildCieMrtd(
                    CieGeneratorConstants.SURNAME,
                    CieGeneratorConstants.GIVEN_NAME,
                    CieGeneratorConstants.DOCUMENT_NUMBER,
                    CieGeneratorConstants.NATIONALITY,
                    CieGeneratorConstants.DATE_OF_BIRTH,
                    CieGeneratorConstants.SEX,
                    CieGeneratorConstants.EXPIRY_DATE,
                    codiceFiscale,
                    CieGeneratorConstants.PLACE_OF_BIRTH,
                    caCertAndKey.keyPair().getPrivate(),
                    caCertAndKey.certificate()
            );


            CieValidationData validationData = new CieValidationData();


            validationData.setCieMrtd(mrtd);
            validationData.setCieIas(ias);
            validationData.setNonce(nonce);
            validationData.setCodFiscDelegante(codiceFiscale);

            validationData.setSignedNonce(ChallengeResponseBuilder.generateSignedNonce(nonce,caCertAndKey.keyPair().getPrivate()));

            // CONVERT TO CIECAANDKEY
            CieCaAndkey cieCaAndkey = new CieCaAndkey();
            cieCaAndkey.setCertPem(caCertAndKey.certificate().getEncoded());
            cieCaAndkey.setCertKey(caCertAndKey.keyPair().getPublic().getEncoded());
            //

            //EXPORT FILES
            CieFileGenerator generator = new CieFileGenerator(validationData,cieCaAndkey,outputDir.toAbsolutePath().toString());
            generator.generateFiles().keySet().stream().forEach(key -> {
                log.info("Exported file: {} ",key);
            });
            //
            return validationData;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
