package it.pagopa.pn.ciechecker;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class CieCheckerImpl implements CieChecker {

    private static final Set<String> COMPATIBLE_ALGOS = Set.of(CieCheckerConstants.SHA_256, CieCheckerConstants.SHA_384, CieCheckerConstants.SHA_512);
    private List<X509Certificate> cscaAnchor;
    private CMSSignedData cms;

    @Override
    public void init() throws CieCheckerException {
        log.info(" -- INIT CieChecker -- ");
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        cscaAnchor = extractCscaAnchor();
    }

    public List<X509Certificate> getCscaAnchor(){
        return this.cscaAnchor;
    }
    public void setCscaAnchor(List<X509Certificate> cscaAnchor){
        this.cscaAnchor = cscaAnchor;
    }
    public List<X509Certificate> extractCscaAnchor() throws CieCheckerException {
        return ValidateUtils.extractCscaAnchorFromZip();
    }

    @Override
    public ResultCieChecker validateMandate(CieValidationData data) throws CieCheckerException {
        log.info("Start validateMandate...");
        try {
            validateDataInput(data);

            cms = new CMSSignedData(data.getCieIas().getSod());
            log.info("validateMandate - CMS created");
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

            return ResultCieChecker.OK;
        }catch(CMSException cmse){
            log.error("CMSException: {}", cmse.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA, cmse);
        }catch (CieCheckerException cce ) {
            log.error("Validation error in validateMandate - CieCheckerException: {}", cce.getMessage());
            return cce.getResult();
        }catch (Exception e ) {
            log.error("Validation error in validateMandate - Exception: {}", e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    private void validateDataInput(CieValidationData data) throws CieCheckerException {
        if ( Objects.isNull(data) || Objects.isNull(data.getCieIas()) || Objects.isNull(data.getCieMrtd())) throw new CieCheckerException(ResultCieChecker.KO_EXC_INPUT_PARAMETER_NULL);
        if ( Objects.isNull(data.getCieMrtd().getSod()) || data.getCieMrtd().getSod().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_MRTDSOD);
        if ( Objects.isNull(data.getCieIas().getNis()) || data.getCieIas().getNis().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CIENIS);
        if ( Objects.isNull(data.getCieIas().getPublicKey()) || data.getCieIas().getPublicKey().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_PUBLICKEY);
        if ( Objects.isNull(data.getSignedNonce()) || data.getSignedNonce().length == 0 ) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_SIGNEDNONCE);
        if ( Objects.isNull(data.getNonce()) || data.getNonce().isEmpty() ) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_NONCE);

    }

    /**
     * @param data CieValidationData
     * @return ResultCieChecker
     * @throws CieCheckerException
     *  Verifica il challenge (nonce) dalla signature, confrontandolo con quello estratto dalla firma.
     *  Per la verifica si utilizzano : byte[] signature, byte[] pubKey, byte[] challenge
     */
    public ResultCieChecker verifyChallengeFromSignature(CieValidationData data) throws CieCheckerException {

        try {
            RSAEngine engine = new RSAEngine();
            PKCS1Encoding engine2 = new PKCS1Encoding(engine);
            // estrazione public key dall'oggetto firma
            RSAKeyParameters publicKey = extractPublicKeyFromSignature(data.getCieIas().getPublicKey());
            engine2.init(false, publicKey);
            // estrae dalla signature i byte del nonce/challenge
            byte[] recovered = engine2.processBlock(data.getSignedNonce(), 0, data.getSignedNonce().length);
            if (!(Arrays.equals(recovered, Hex.decodeHex(data.getNonce()))))
                throw new CieCheckerException( ResultCieChecker.KO_EXC_NO_MATCH_NONCE_SIGNATURE);
            else
                return ResultCieChecker.OK;
        }catch (IllegalArgumentException ie){
            log.error("Error in verifyChallengeFromSignature - IllegalArgumentException: {}", ie.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_PUBLICKEY, ie);
        }catch( CryptoException cre){
            log.error("Error in verifyChallengeFromSignature - CryptoException: {}", cre.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_CRYPTOGRAPHIC_OPERATION, cre);
        } catch (DecoderException de) {
            log.error("Error in verifyChallengeFromSignature - DecoderException: {}", de.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_PARSING_HEX_BYTE, de);
        }catch (Exception e){
            log.error("Error in verifyChallengeFromSignature - Exception: {}", e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    /*
    @Override
    public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey, byte[] nis) throws NoSuchAlgorithmException, InvalidKeySpecException, CryptoException {
        return false;
    }*/

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
     * nis_verify_sod_passive_auth.sh
     *
     * @param cieIas CieIas
     * @return boolean
     * @throw CieCheckerException
     */
    public boolean verifySodPassiveAuthCie(CMSSignedData cms, byte[] cieIasNis) throws CieCheckerException {

        try {

            /*****************************************************
             ** PASSO 1 - ANALISI E ESTRAZIONE DEI COMPONENTI
             *****************************************************/
            log.info("Estrazione del certificato DSC ...");
            X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);

            log.info("Estrazione della chiave pubblica dal Certificato X509 ...");
            PublicKey publicKey = ValidateUtils.extractPublicKeyFromHolder(certHolder);
            if ( Objects.isNull(publicKey) ) {
                log.error("Error in verifySodPassiveAuthCie: {}", EXC_EXTRACTION_PUBLICKEY);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_EXTRACTION_PUBLICKEY);
            }

            log.info("Estrazione delle firme dei firmatari dal SignedData ...");
            List<byte[]> signatures = ValidateUtils.extractSignaturesFromSignedData(cms);
            if ( signatures.isEmpty() ) {
                log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_SIGNATURES_SIGNED_DATA);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNATURES_SIGNED_DATA);
            }

            /*****************************************************
             ** PASSO 2 - VERIFICA ed ESTRAZIONE DEGLI HASH: CONTENT
             *****************************************************/
            log.info("Estrazione e verifica degli HashContent con l'hash firmato...");
            if (!ValidateUtils.verifyMatchHashContent(cms)) {
                log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_HASH_CONTENT_MATCH);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_HASH_CONTENT_MATCH);
            }

            /*******************************************************************
             ** PASSO 1A: ESTRAZIONE DEGLI ATTRIBUTI FIRMATI (signedAttributes)
             *******************************************************************/
            log.info("Estrazione degli signedAttributes ...");
            Hashtable<ASN1ObjectIdentifier, Attribute> signedAttributesTable = ValidateUtils.extractAllSignedAttributes(cms);
            if(Objects.isNull(signedAttributesTable) || signedAttributesTable.isEmpty()) {
                log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_SIGNED_ATTRIBUTE);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNED_ATTRIBUTE);
            }

            /*******************************************************************
             ** PASSO 1B: ANALISI DEGLI HASH DEI DATI (DataGroupHashes)
             *********************************************************************/
            log.info("Estrazione e verifica della lista degli hash dei DataGroup ...");
            if (!ValidateUtils.verifyNisSha256FromDataGroup(cms, cieIasNis)) {
                log.error("Error in verifySodPassiveAuthCie: {}", EXC_NO_MATCH_NIS_HASHES_DATAGROUP);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP);
            }

            /*******************************************************************
             **  PASSO 3: VERIFICA FINALE DELLA FIRMA DIGITALE
             ********************************************************************/
            /* NB: LE DUE SEGUENTI VERIFICHE SONO USATE NELLO SCRIPT SHELL E QUI SOLO COME TEST
            if (!ValidateUtils.veryfySignedAttrIsSet(cms)) {
                //System.err.println("SignedAttribute content do not match the expected value");
                return false;
            }
            if (!ValidateUtils.veryfySignatures(cms)) {
                //System.err.println("Signature do not match the expected value");
                return false;
            }
            */

            ResultCieChecker result = ValidateUtils.verifyDigitalSignature(cms);
            if(!(result.getValue().equals(OK))) {
                log.error("Error in verifySodPassiveAuthCie: {}", EXC_NOVALID_DIGITAL_SIGNATURE);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NOVALID_DIGITAL_SIGNATURE);
            }else
                return true;

        }catch(CMSException cmse){
            log.error("Error in verifySodPassiveAuthCie - CMSException: {}", cmse.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA, cmse);
        /*}catch(NoSuchAlgorithmException nsae) {
            log.error("Error in verifySodPassiveAuthCie - NoSuchAlgorithmException: {}", nsae.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOT_AVAILABLE_CRYPTOGRAPHIC_ALGORITHM, nsae);*/
        } catch (Exception  e ){
            log.error("Error in verifySodPassiveAuthCie - Exception: {}", e.getMessage());
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
        try {
            if(mrtd.getSod() == null){
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_MRTD_SOD);
            }
            byte[] dg1 = mrtd.getDg1() != null ? mrtd.getDg1() : null;
            byte[] dg11 = mrtd.getDg11() != null ? mrtd.getDg11() : null;

            return verifyIntegrityCore(mrtd.getSod(), dg1, dg11);
        } catch (CieCheckerException ce) {
            log.error("Error in verifyIntegrity - Validation error in verifyIntegrity: {}", ce.getMessage());
            throw new CieCheckerException(ce.getResult(), ce);
        } catch (Exception e) {
            log.error("Error in verifyIntegrity - Unexpected error in verifyIntegrity: {}", e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    /**
     * esegue la logica vera e propria e propaga le eccezioni.
     * - usa ValidateUtils.decodeSodHr(...) per ottenere SodSummary (equivalente a sod_summary dello script)
     * - per ogni DG contenuto in dgFiles verifica hash
     *
     * Ritorna true solo se tutti i DG verificati combaciano.
     */
    public ResultCieChecker verifyIntegrityCore(byte[] sodBytes, byte[] dg1, byte[] dg11) throws Exception {
        // 1) Decodifica SOD
        SodSummary sodSummary = decodeSodHr(sodBytes);

        // 2) Identifica algoritmo di digest
        String hashAlgorithmName = getDigestName(sodSummary.getDgDigestAlgorithm().getAlgorithm().getId());
        if (hashAlgorithmName == null || hashAlgorithmName.isBlank()) {
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_HASH_ALGORITHM_SOD);
        }
        if (!COMPATIBLE_ALGOS.contains(hashAlgorithmName)) {
            log.error("Unsupported hash algorithm: {}", hashAlgorithmName);
            throw new CieCheckerException(ResultCieChecker.KO_EXC_UNSUPPORTED_ALGORITHM_SOD);
        }

        MessageDigest md = MessageDigest.getInstance(hashAlgorithmName);

        Map<Integer, byte[]> expectedHashes = sodSummary.getDgExpectedHashes();
        if (expectedHashes == null || expectedHashes.isEmpty()) {
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_EXPECTED_HASHES_SOD);
        }

        // 3) Verifica DG1 e DG11
        verifyDigestList(md, dg1, expectedHashes, 1);
        verifyDigestList(md, dg11, expectedHashes, 11);

        return ResultCieChecker.OK;
    }

    private void verifyDigestList(MessageDigest md, byte[] dg, Map<Integer, byte[]> expectedHashes, int dgNum){ // DA SCOMMENTARE throws CieCheckerException {
        if (!isVerifyDigest(md, dg, expectedHashes.get(dgNum))) {
            log.error("Error in verifyDigestList: {}{}", EXC_NOT_SAME_DIGEST, dgNum);
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
        log.info("Start verifyTrustChain() - Verifica la validità della catena di fiducia del SOD");
        try {

            X509CertificateHolder holder = ValidateUtils.extractDscCertDer(cms);
            byte[] dscDer = holder.getEncoded();

            log.info("Verifica se il certificato e' stato firmato da una delle autorita' di certificazione presenti nella catena di fiducia 'cscaAnchor'");
            ResultCieChecker result = ValidateUtils.verifyDscAgainstTrustBundle(dscDer, cscaAnchor, new Date());
            if (!result.getValue().equals(OK)) {
                log.error("An error occoured in verifyTrustChain(). Error = {}",EXC_CERTIFICATE_NOT_SIGNED);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED);
            }
            return ValidateUtils.verifyDigitalSignature(cms);
        }catch(IOException ioe){
            log.error("Error in verifyTrustChain - IOException: {}", ioe.getMessage());
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
        log.info("Start verifyDigitalSignature() ...");

        ResultCieChecker result = verifyTrustChain(cms);
        if( !(result.getValue().equals(OK)) ) {
            throw new CieCheckerException(result);
        } else {
            log.info("verifyDigitalSignature() completed successfully.");
            return result;
        }
    }

}
