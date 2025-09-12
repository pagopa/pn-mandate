package it.pagopa.pn.ciechecker;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;

import java.security.spec.InvalidKeySpecException;
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
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import static it.pagopa.pn.ciechecker.utils.ValidateUtils.*;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

@Slf4j
public class CieCheckerImpl implements CieChecker {

    private static final Set<String> COMPATIBLE_ALGOS = Set.of(CieCheckerConstants.SHA_256, CieCheckerConstants.SHA_384, CieCheckerConstants.SHA_512);

    @Override
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public ResultCieChecker validateMandate(CieValidationData data)  {
        try {
            //16048-bis - NIS: nis_verify_sod.sh
            verifyDigitalSignature(data.getCieIas().getSod(), data.getCieIas().getCscaAnchor());

            //16049 NIS: nis_verify_sod_passive_auth.sh
            verifySodPassiveAuthCie(data.getCieIas());

            //16050 NIS: nis_verify_challenge.sh - verifica del nonce - verificare la firma di una challenge IAS

            //16051 MRTD: verify_integrity.sh
            verifyIntegrity(data.getCieMrtd());

            //16052 MRTD: verify_signature.sh
            //verifyDigitalSignature(data.getCieMrtd().getSod(), data.getCieMrtd().getCscaAnchor());

            return ResultCieChecker.OK;

        }catch (CieCheckerException e ){
            System.err.println("CieCheckerException: " + e.getResult().getValue());
            return e.getResult();
        }
    }

    /**
     *
     * @param signature
     * @param pubKey
     * @param challenge
     * @return
     * @throws CryptoException
     *
     *  Verifica il challenge (nonce) dalla signature, confrontandolo con quello estratto dalla firma.
     */
    public boolean verifyChallengeFromSignature(byte[] signature, byte[] pubKey, byte[] challenge) throws  CryptoException {
        RSAEngine engine = new RSAEngine();
        PKCS1Encoding engine2 = new PKCS1Encoding(engine);
        // estrazione public key dall'oggetto firma
        RSAKeyParameters publicKey = extractPublicKeyFromSignature(pubKey);
        engine2.init(false, publicKey);
        // estrae dalla signature i byte del nonce/challenge
        byte[] recovered = engine2.processBlock(signature, 0, signature.length);
        return Arrays.equals(recovered, challenge);
    }

    @Override
    public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey, byte[] nis) throws NoSuchAlgorithmException, InvalidKeySpecException, CryptoException {
        return false;
    }

    /**
     *
     * @param pubKey
     * @return
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
     */
    public boolean verifySodPassiveAuthCie(CieIas cieIas) {

        try {
            if (cieIas == null || cieIas.getSod() == null || cieIas.getNis() == null ) {
                throw new CieCheckerException(EXC_INPUT_PARAMETER_NULL);
            }
            CMSSignedData cms = new CMSSignedData(cieIas.getSod());

            /*****************************************************
             ** PASSO 1 - ANALISI E ESTRAZIONE DEI COMPONENTI
             *****************************************************/
            // - Estrazione del certificato DSC
            X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);
            // - Estrazione della chiave pubblica dal Certificato X509
            PublicKey publicKey = ValidateUtils.extractPublicKeyFromHolder(certHolder);
            // - Estrazione delle firme di ogni firmatario dal SignedData
            List<byte[]> signatures = ValidateUtils.extractSignaturesFromSignedData(cms);
            if(signatures == null || signatures.isEmpty())
                throw new CieCheckerException(EXC_NO_SIGNATURES_SIGNED_DATA);

            /*****************************************************
             ** PASSO 2 - VERIFICA ed ESTRAZIONE DEGLI HASH: CONTENT
             *****************************************************/
            if (!ValidateUtils.verifyMatchHashContent(cms)) {
                System.err.println(EXC_NO_HASH_CONTENT_MATCH);
                return false;
            }

            /*******************************************************************
             ** PASSO 1A: ESTRAZIONE DEGLI ATTRIBUTI FIRMATI (signedAttributes)
             *******************************************************************/
            Hashtable<ASN1ObjectIdentifier, Attribute> signedAttributesTable = ValidateUtils.extractAllSignedAttributes(cms);
            if(signedAttributesTable == null || signedAttributesTable.isEmpty())
                throw new CieCheckerException(EXC_NO_SIGNED_ATTRIBUTE);

            /*******************************************************************
             ** PASSO 1B: ANALISI DEGLI HASH DEI DATI (DataGroupHashes)
             *********************************************************************/
            // Estrazione e verifica della lista degli hash dei Data Group
            if (!ValidateUtils.verifyNisSha256FromDataGroup(cms, cieIas.getNis())) {
                System.err.println(EXC_NO_MATCH_NIS_HASHES_DATAGROUP);
                return false;
            }
            /*******************************************************************
             ** PASSO 2: MANIPOLAZIONE DEL TAG PER LA VERIFICA DELLA FIRMA
             *******************************************************************/
            //Quando viene richiesto l'array di byte con getEncoded(), BouncyCastle formatta correttamente,
            //aggiungendo il tag 0x31 e la lunghezza, in modo che l'output sia pronto per l'algoritmo di hash SHA-256.

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
            return result.getValue().equals("OK");

        } catch (CieCheckerException | CMSException | IOException | NoSuchAlgorithmException  e ){
            System.err.println("Error during verification SOD: " + e.getMessage());
            return false;
        }
    }
    /**
     * accetta path EF.SOD e una lista variabile di path EF.DG* (varargs, così possiamo
     * passare un numero variabile di parametri senza dover creare un array a mano)
     *
     * questo metodo replica il comportamento dello script verify_integrity.sh:
     * 1) decodifica il SOD (ValidateUtils.decodeSodHr)
     * 2) identifica l'algoritmo di hash
     * 3) verifica che sia compatibile
     * 4) per ogni DG passato ricalcola l'hash e lo confronta con quello nel SOD
     *
     * In caso di eccezione ritorna false (puoi cambiare la gestione se preferisci lanciare eccezioni).
     */
    @Override
    public ResultCieChecker verifyIntegrity(CieMrtd mrtd) {
        try {
            if(mrtd.getSod() == null){
                throw new CieCheckerException(ResultCieChecker.KO_NOTFOUND_MRTD_SOD);
            }
            byte[] dg1 = mrtd.getDg1() != null ? mrtd.getDg1() : null;
            byte[] dg11 = mrtd.getDg11() != null ? mrtd.getDg11() : null;
            System.out.println("DG11: "+dg11);

            return verifyIntegrityCore(mrtd.getSod(), dg1, dg11);
        } catch (CieCheckerException e) {
            log.error("Validation error in verifyIntegrity: {}", e.getMessage());
            return e.getResult();
        } catch (Exception e) {
            log.error("Unexpected error in verifyIntegrity: {}", e.getMessage());
            return ResultCieChecker.KO;
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
            throw new CieCheckerException(ResultCieChecker.KO_HASH_ALGORITHM_SOD);
        }
        if (!COMPATIBLE_ALGOS.contains(hashAlgorithmName)) {
            log.error("Unsupported hash algorithm: {}", hashAlgorithmName);
            throw new CieCheckerException(ResultCieChecker.KO_UNSUPPORTED_ALGORITHM_SOD);
        }

        MessageDigest md = MessageDigest.getInstance(hashAlgorithmName);

        Map<Integer, byte[]> expectedHashes = sodSummary.getDgExpectedHashes();
        if (expectedHashes == null || expectedHashes.isEmpty()) {
            throw new CieCheckerException(ResultCieChecker.KO_NOTFOUND_EXPECTED_HASHES_SOD);
        }

        // 3) Verifica DG1 e DG11
        verifyDigestList(md, dg1, expectedHashes, 1);
        verifyDigestList(md, dg11, expectedHashes, 11);

        return ResultCieChecker.OK;
    }

    private void verifyDigestList(MessageDigest md, byte[] dg, Map<Integer, byte[]> expectedHashes, int dgNum) throws CieCheckerException {
            if (!isVerifyDigest(md, dg, expectedHashes.get(dgNum))) {
                throw new CieCheckerException(ResultCieChecker.KO_NOT_SAME_DIGEST);
            }

    }

    /**
     * Verifica la validità della catena di fiducia del SOD
     *
     * @param cms CMSSignedData
     * @param cscaAnchors List<byte[]>
     * @return ResultCieChecker
     */
    public ResultCieChecker verifyTrustChain(CMSSignedData cms, List<byte[]> cscaAnchors) throws IOException {
        try {
            /*if (cieMrtd == null || cieMrtd.getSod() == null || cieMrtd.getCscaAnchor() == null || cieMrtd.getCscaAnchor().isEmpty()) {
                throw new CieCheckerException(EXC_INPUT_PARAMETER_NULL);
            }
            CMSSignedData cms = new CMSSignedData(cieMrtd.getSod());
             */
            X509CertificateHolder holder = ValidateUtils.extractDscCertDer(cms);
            byte[] dscDer = holder.getEncoded();
            //verifica se il certificato contenuto in $DSC_DER_FILE è stato firmato da una delle autorità di certificazione presenti nel file $TRUST_BUNDLE_PEM.
            ResultCieChecker result = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, cscaAnchors, new Date());
            if (!result.getValue().equals(OK)) {
                System.err.println(EXC_CERTIFICATE_NOT_SIGNED);
                //return ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED;
                throw new CieCheckerException(ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED);
            }
            return ValidateUtils.verifyDigitalSignature(cms);
        }catch (CieCheckerException cce) {
            return cce.getResult();
        }
        /*catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        }*/
    }


    /**
     * Verifica la validità della firma e della catena di fiducia del SOD
     * nis_verify_sod.sh / verify_signature.sh
     *
     * @param sod byte[]
     * @param cscaTrustAnchors List<byte[]>
     * @return boolean
     */
    @Override
    public ResultCieChecker verifyDigitalSignature(byte[] sod, List<byte[]> cscaTrustAnchors) {

        try {
            //if (Objects.isNull(sod) || sod.length == 0 || Objects.isNull(cscaTrustAnchors) || cscaTrustAnchors.isEmpty())
            //    throw new CieCheckerException( ResultCieChecker.KO_INPUT_PARAMETER_NULL);
            if (Objects.isNull(sod) || sod.length == 0) throw new CieCheckerException(EXC_INPUT_PARAMETER_NULL);
            if (Objects.isNull(cscaTrustAnchors) || cscaTrustAnchors.isEmpty()) throw new CieCheckerException(NO_CSCA_ANCHORS_PROVIDED);

            CMSSignedData cms = new CMSSignedData(sod);
            ResultCieChecker result = verifyTrustChain(cms, cscaTrustAnchors);
            if( !(result.getValue().equals(OK)) )
                throw new CieCheckerException(result);
            else
                return result;
        }catch(CieCheckerException cc){
            System.err.println("CieCheckerException: " + cc.getMessage());
            throw new CieCheckerException(cc.getMessage());
        }catch(CMSException cmse){
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA);
            //return ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA;
        }catch(IOException ioe){
            throw new CieCheckerException(ResultCieChecker.KO_EXC_IOEXCEPTION);
            //return ResultCieChecker.KO_EXC_IOEXCEPTION;
        }
    }

}
