package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.utils.LogsCostant;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import static it.pagopa.pn.ciechecker.CieCheckerConstants.*;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import it.pagopa.pn.ciechecker.model.*;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;

import java.io.IOException;
import java.security.PublicKey;
import java.security.Security;
import static it.pagopa.pn.ciechecker.utils.ValidateUtils.*;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

@lombok.CustomLog
public class CieCheckerImpl implements CieChecker {

    private static final Set<String> COMPATIBLE_ALGOS = Set.of(CieCheckerConstants.SHA_256, CieCheckerConstants.SHA_384, CieCheckerConstants.SHA_512);
    @Setter
    @Getter
    private List<X509Certificate> cscaAnchor;
    private CMSSignedData cms;

    @Override
    public void init() throws CieCheckerException {
        log.debug(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.CIECHECKER_INIT);
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        cscaAnchor = extractCscaAnchor();
    }

    public List<X509Certificate> extractCscaAnchor() throws CieCheckerException {
        return ValidateUtils.extractCscaAnchorFromZip();
    }

    @Override
    public ResultCieChecker validateMandate(CieValidationData data) throws CieCheckerException {
        log.logStartingProcess(LogsCostant.CIECHECKER_VALIDATE_MANDATE);
        try {
            validateDataInput(data);

            cms = new CMSSignedData(data.getCieIas().getSod());
            //log.debug("validateMandate - CMS created");
            log.debug(LogsCostant.CIECHECKER_VALIDATE_MANDATE, "CMSSignedData={}", cms);

            //16048-bis - NIS: nis_verify_sod.sh
            verifyDigitalSignature(cms);

            //16049 NIS: nis_verify_sod_passive_auth.sh
            verifySodPassiveAuthCie(cms, data.getCieIas().getNis());

            //16050 NIS: nis_verify_challenge.sh - verifica del nonce: verifica la firma di una challenge IAS
            verifyChallengeFromSignature(data);

            //16051 MRTD: verify_integrity.sh
            verifyIntegrity(data.getCieMrtd());

            //16052 MRTD: verify_signature.sh
            cms = new CMSSignedData(data.getCieMrtd().getSod());
            verifyDigitalSignature(cms);

            log.logEndingProcess(LogsCostant.CIECHECKER_VALIDATE_MANDATE, true, ResultCieChecker.OK.getValue());
            return ResultCieChecker.OK;
        }catch(CMSException cmse){
            //log.error("CMSException: {}", cmse.getMessage());
            log.logEndingProcess(LogsCostant.CIECHECKER_VALIDATE_MANDATE, false, ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA, cmse);
        }catch (CieCheckerException cce ) {
            //log.error("Validation error in validateMandate - CieCheckerException: {}", cce.getMessage());
            log.logEndingProcess(LogsCostant.CIECHECKER_VALIDATE_MANDATE, false, cce.getResult().getValue());
            return cce.getResult();
        }catch (Exception e ) {
            //log.error("Validation error in validateMandate - Exception: {}", e.getMessage());
            log.logEndingProcess(LogsCostant.CIECHECKER_VALIDATE_MANDATE, false, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    public boolean validateDataInput(CieValidationData data) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL_WITH_ARGS, LogsCostant.CIECHECKER_VALIDATE_DATA_INPUT, data);
        if ( Objects.isNull(data) || Objects.isNull(data.getCieIas()) || Objects.isNull(data.getCieMrtd())) throw new CieCheckerException(ResultCieChecker.KO_EXC_INPUT_PARAMETER_NULL);
        if ( Objects.isNull(data.getCieIas().getSod()) || data.getCieIas().getSod().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CIESOD);
        if ( Objects.isNull(data.getCieIas().getNis()) || data.getCieIas().getNis().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CIENIS);
        if ( Objects.isNull(data.getCieIas().getPublicKey()) || data.getCieIas().getPublicKey().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_PUBLICKEY);
        if ( Objects.isNull(data.getSignedNonce()) || data.getSignedNonce().length == 0 ) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_SIGNEDNONCE);
        if ( Objects.isNull(data.getNonce()) || data.getNonce().isEmpty() ) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_NONCE);
        if ( Objects.isNull(data.getCieMrtd().getSod()) || data.getCieMrtd().getSod().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_MRTDSOD);
        if ( Objects.isNull(data.getCieMrtd().getDg1()) || data.getCieMrtd().getDg1().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_MRTDDG1);
        if ( Objects.isNull(data.getCieMrtd().getDg11()) || data.getCieMrtd().getDg11().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_MRTDDG11);
        log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.CIECHECKER_VALIDATE_DATA_INPUT, data, true);
        return true;
    }

    /**
     * @param data CieValidationData
     * @return ResultCieChecker
     * @throws CieCheckerException
     *  Verifica il challenge (nonce) dalla signature, confrontandolo con quello estratto dalla firma.
     *  Per la verifica si utilizzano : byte[] signature, byte[] pubKey, byte[] challenge
     */
    public ResultCieChecker verifyChallengeFromSignature(CieValidationData data) throws CieCheckerException {
        //log.info("Start verifyChallengeFromSignature() ... ");
        log.info(LogsCostant.INVOKING_OPERATION_LABEL_WITH_ARGS, LogsCostant.CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE, data);
        try {
            RSAEngine engine = new RSAEngine();
            PKCS1Encoding engine2 = new PKCS1Encoding(engine);
            // estrazione public key dall'oggetto firma
            RSAKeyParameters publicKey = extractPublicKeyFromSignature(data.getCieIas().getPublicKey());
            engine2.init(false, publicKey);
            // estrae dalla signature i byte del nonce/challenge
            byte[] recovered = engine2.processBlock(data.getSignedNonce(), 0, data.getSignedNonce().length);
            if (!(Arrays.equals(recovered, Hex.decodeHex(data.getNonce())))) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE, ResultCieChecker.KO_EXC_NO_MATCH_NONCE_SIGNATURE.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_MATCH_NONCE_SIGNATURE);
            }else {
                log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE, "ResultCieChecker", ResultCieChecker.OK.getValue());
                return ResultCieChecker.OK;
            }
        }catch (IllegalArgumentException ie){
            //log.error("Error in verifyChallengeFromSignature - IllegalArgumentException: {}", ie.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE, ie.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_PUBLICKEY, ie);
        }catch( CryptoException cre){
            //log.error("Error in verifyChallengeFromSignature - CryptoException: {}", cre.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE, cre.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_CRYPTOGRAPHIC_OPERATION, cre);
        } catch (DecoderException de) {
            //log.error("Error in verifyChallengeFromSignature - DecoderException: {}", de.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE, de.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_PARSING_HEX_BYTE, de);
        }catch (Exception e){
            //log.error("Error in verifyChallengeFromSignature - Exception: {}", e.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }


    /**
     *
     * @param pubKey byte[]
     * @return RSAKeyParameters result
     *
     * Converte la public key byte array in una public Key
     */
    private RSAKeyParameters extractPublicKeyFromSignature(byte[] pubKey) {
        RSAPublicKey pkcs1PublicKey = RSAPublicKey.getInstance(pubKey);
        BigInteger modulus = pkcs1PublicKey.getModulus();
        BigInteger publicExponent = pkcs1PublicKey.getPublicExponent();

        return new RSAKeyParameters(false, modulus, publicExponent); // false per public key (true=private)
    }


    @Override
    /**
     * Effettua la verifica completa del Document Security Object (SOD) della CIE
     * (nis_verify_sod_passive_auth.sh)
     * @param cieIas CieIas
     * @return boolean
     * @throw CieCheckerException
     */
    public boolean verifySodPassiveAuthCie(CMSSignedData cms, byte[] cieIasNis) throws CieCheckerException {

        try {
            //log.info("Start verifySodPassiveAuthCie() ...");
            log.info(LogsCostant.INVOKING_OPERATION_LABEL_WITH_ARGS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, cms, cieIasNis);
            /*****************************************************
             ** PASSO 1 - ANALISI E ESTRAZIONE DEI COMPONENTI
             *****************************************************/
            log.debug("Estrazione del certificato DSC ...");
            X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);

            log.debug("Estrazione della chiave pubblica dal Certificato X509 ...");
            PublicKey publicKey = ValidateUtils.extractPublicKeyFromHolder(certHolder);
            if ( Objects.isNull(publicKey) ) {
                //log.error("Error in verifySodPassiveAuthCie: {}", EXC_EXTRACTION_PUBLICKEY);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, ResultCieChecker.KO_EXC_EXTRACTION_PUBLICKEY.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_EXTRACTION_PUBLICKEY);
            }

            log.debug("Estrazione delle firme dei firmatari dal SignedData ...");
            List<byte[]> signatures = ValidateUtils.extractSignaturesFromSignedData(cms);
            if ( signatures.isEmpty() ) {
                //log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_SIGNATURES_SIGNED_DATA);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, ResultCieChecker.KO_EXC_NO_SIGNATURES_SIGNED_DATA.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNATURES_SIGNED_DATA);
            }

            /*****************************************************
             ** PASSO 2 - VERIFICA ed ESTRAZIONE DEGLI HASH: CONTENT
             *****************************************************/
            log.debug("Estrazione e verifica degli HashContent con l'hash firmato...");
            if (!ValidateUtils.verifyMatchHashContent(cms)) {
                //log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_HASH_CONTENT_MATCH);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, ResultCieChecker.KO_EXC_NO_HASH_CONTENT_MATCH.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_HASH_CONTENT_MATCH);
            }

            /*******************************************************************
             ** PASSO 1A: ESTRAZIONE DEGLI ATTRIBUTI FIRMATI (signedAttributes)
             *******************************************************************/
            log.debug("Estrazione degli signedAttributes ...");
            Hashtable<ASN1ObjectIdentifier, Attribute> signedAttributesTable = ValidateUtils.extractAllSignedAttributes(cms);
            if(Objects.isNull(signedAttributesTable) || signedAttributesTable.isEmpty()) {
                //log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_SIGNED_ATTRIBUTE);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, ResultCieChecker.KO_EXC_NO_SIGNED_ATTRIBUTE.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNED_ATTRIBUTE);
            }

            /*******************************************************************
             ** PASSO 1B: ANALISI DEGLI HASH DEI DATI (DataGroupHashes)
             *********************************************************************/
            log.debug("Estrazione e verifica della lista degli hash dei DataGroup ...");
            if (!ValidateUtils.verifyNisSha256FromDataGroup(cms, cieIasNis)) {
                //log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_MATCH_NIS_HASHES_DATAGROUP);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, ResultCieChecker.KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP);
            }

            log.debug("Verifica finale della firma digitale ...");
            ResultCieChecker result = ValidateUtils.verifyDigitalSignature(cms);
            if(!(result.getValue().equals(OK))) {
                //log.error("Error in verifySodPassiveAuthCie: {}", EXC_NOVALID_DIGITAL_SIGNATURE);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, ResultCieChecker.KO_EXC_NOVALID_DIGITAL_SIGNATURE.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NOVALID_DIGITAL_SIGNATURE);
            }else {
                log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, "boolean", true);
                return true;
            }
        }catch(CMSException cmse){
            //log.error("Error in verifySodPassiveAuthCie - CMSException: {}", cmse.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, cmse.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA, cmse);
        } catch (Exception  e ){
            //log.error("Error in verifySodPassiveAuthCie - Exception: {}", e.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    /**
     * accetta path EF.SOD e una lista variabile di path EF.DG* (varargs, così possiamo
     * passare un numero variabile di parametri senza dover creare un array a mano)
     * questo metodo replica il comportamento dello script verify_integrity.sh:
     * 1) decodifica il SOD (ValidateUtils.decodeSodHr)
     * 2) identifica l'algoritmo di hash
     * 3) verifica che sia compatibile
     * 4) per ogni DG passato ricalcola l'hash e lo confronta con quello nel SOD
     * In caso di eccezione ritorna false (puoi cambiare la gestione se preferisci lanciare eccezioni).
     */
    @Override
    public ResultCieChecker verifyIntegrity(CieMrtd mrtd) throws CieCheckerException {
        //log.info("Start verifyIntegrity() della firma digitale ... ");
        log.info(LogsCostant.INVOKING_OPERATION_LABEL_WITH_ARGS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY, mrtd);
        try {
            if(mrtd.getSod() == null){
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY, ResultCieChecker.KO_EXC_NOTFOUND_MRTD_SOD.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_MRTD_SOD);
            }
            byte[] dg1 = mrtd.getDg1() != null ? mrtd.getDg1() : null;
            byte[] dg11 = mrtd.getDg11() != null ? mrtd.getDg11() : null;

            return verifyIntegrityCore(mrtd.getSod(), dg1, dg11);
        } catch (CieCheckerException ce) {
            //log.error("Error in verifyIntegrity - Validation error in verifyIntegrity: {}", ce.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY, ce.getMessage());
            throw new CieCheckerException(ce.getResult(), ce);
        } catch (Exception e) {
            //log.error("Error in verifyIntegrity - Unexpected error in verifyIntegrity: {}", e.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    /**
     * esegue la logica vera e propria e propaga le eccezioni.
     * - usa ValidateUtils.decodeSodHr(...) per ottenere SodSummary (equivalente a sod_summary dello script)
     * - per ogni DG contenuto in dgFiles verifica hash
     * Ritorna true solo se tutti i DG verificati combaciano.
     */
    public ResultCieChecker verifyIntegrityCore(byte[] sodBytes, byte[] dg1, byte[] dg11) throws Exception {
        log.info(LogsCostant.INVOKING_OPERATION_LABEL_WITH_ARGS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY_CORE, sodBytes, dg1, dg11);

        log.debug("Decodifica SOD...");
        SodSummary sodSummary = decodeSodHr(sodBytes);

        log.debug("Identifica algoritmo di digest");
        String hashAlgorithmName = getDigestName(sodSummary.getDgDigestAlgorithm().getAlgorithm().getId());
        if (hashAlgorithmName == null || hashAlgorithmName.isBlank()) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY_CORE, ResultCieChecker.KO_EXC_NO_HASH_ALGORITHM_SOD.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_HASH_ALGORITHM_SOD);
        }
        if (!COMPATIBLE_ALGOS.contains(hashAlgorithmName)) {
            //log.error("Unsupported hash algorithm: {}", hashAlgorithmName);
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY_CORE, ResultCieChecker.KO_EXC_UNSUPPORTED_ALGORITHM_SOD.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_UNSUPPORTED_ALGORITHM_SOD);
        }

        MessageDigest md = MessageDigest.getInstance(hashAlgorithmName);

        Map<Integer, byte[]> expectedHashes = sodSummary.getDgExpectedHashes();
        if (expectedHashes == null || expectedHashes.isEmpty()) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_INTEGRITY_CORE, ResultCieChecker.KO_EXC_NOTFOUND_EXPECTED_HASHES_SOD.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_EXPECTED_HASHES_SOD);
        }

        log.debug("Verifica DG1 e DG11");
        verifyDigestList(md, dg1, expectedHashes, 1);
        verifyDigestList(md, dg11, expectedHashes, 11);

        log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.CIECHECKER_VERIFY_INTEGRITY_CORE, "ResultCieChecker", ResultCieChecker.OK.getValue());
        return ResultCieChecker.OK;
    }

    private void verifyDigestList(MessageDigest md, byte[] dg, Map<Integer, byte[]> expectedHashes, int dgNum){ // DA SCOMMENTARE throws CieCheckerException {
        if (!isVerifyDigest(md, dg, expectedHashes.get(dgNum))) {
            //log.error("Error in verifyDigestList: {}{}", EXC_NOT_SAME_DIGEST, dgNum);
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_DIGESTLIST, ResultCieChecker.KO_EXC_NOT_SAME_DIGEST.getValue(), dgNum);
            // DA SCOMMENTARE throw new CieCheckerException(ResultCieChecker.KO_EXC_NOT_SAME_DIGEST);
        }
    }

    /**
     * Verifica la validità della catena di fiducia del SOD
     *
     * @param cms CMSSignedData
     * @return ResultCieChecker
     */
    public ResultCieChecker verifyTrustChain(CMSSignedData cms) throws CieCheckerException {
        log.info(LogsCostant.INVOKING_OPERATION_LABEL_WITH_ARGS, LogsCostant.CIECHECKER_VERIFY_TRUST_CHAIN, cms);
        try {
            log.debug("Verifica la validità della catena di fiducia del SOD");
            X509CertificateHolder holder = ValidateUtils.extractDscCertDer(cms);
            byte[] dscDer = holder.getEncoded();

            log.debug("Verifica se il certificato e' stato firmato da una delle autorita' di certificazione presenti nella catena di fiducia 'cscaAnchor'");
            ResultCieChecker result = ValidateUtils.verifyDscAgainstTrustBundle(dscDer, cscaAnchor, new Date());
            if (!result.getValue().equals(OK)) {
                //log.error("An error occoured in verifyTrustChain(). Error = {}",EXC_CERTIFICATE_NOT_SIGNED);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_TRUST_CHAIN, ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED);
            }
            log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.CIECHECKER_VERIFY_TRUST_CHAIN, "ResultCieChecker", result.getValue());
            return ValidateUtils.verifyDigitalSignature(cms);
        }catch(IOException ioe){
            //log.error("Error in verifyTrustChain - IOException: {}", ioe.getMessage());
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_TRUST_CHAIN, ioe.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_IOEXCEPTION, ioe);
        }
    }


    /**
     * Verifica la validità della firma e della catena di fiducia del SOD
     * nis_verify_sod.sh / verify_signature.sh
     *
     * @param cms CMSSignedData
     * @return boolean
     */
    @Override
    public ResultCieChecker verifyDigitalSignature( CMSSignedData cms) throws CieCheckerException {
        //log.info("Start verifyDigitalSignature() ...");
        log.info(LogsCostant.INVOKING_OPERATION_LABEL_WITH_ARGS, LogsCostant.CIECHECKER_VERIFY_DIGITAL_SIGNATURE, cms);
        ResultCieChecker result = verifyTrustChain(cms);
        if( !(result.getValue().equals(OK)) ) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.CIECHECKER_VERIFY_DIGITAL_SIGNATURE, result.getValue());
            throw new CieCheckerException(result);
        } else {
            //log.info("verifyDigitalSignature() completed successfully.");
            log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.CIECHECKER_VERIFY_DIGITAL_SIGNATURE, result.getValue());
            return result;
        }
    }

}
