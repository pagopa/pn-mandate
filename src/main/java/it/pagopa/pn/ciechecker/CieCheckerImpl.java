package it.pagopa.pn.ciechecker;

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


public class CieCheckerImpl implements CieChecker {

    private static final Set<String> COMPATIBLE_ALGOS = Set.of(CieCheckerConstants.SHA_256, CieCheckerConstants.SHA_384, CieCheckerConstants.SHA_512);

    @Override
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public boolean validateMandate(CieValidationData data) {



        //NIS: nis_verify_sod_passive_auth.sh
        verificationSodCie(data.getCieIas());

        //MRTD: verify_integrity.sh

        //MRTD: verify_signature.sh
        verifyDigitalSignatureMrtd( data.getCieMrtd());
        return true;
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
    public boolean verificationSodCie(CieIas cieIas) {

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
            return ValidateUtils.verifyDigitalSignature(cms);

        } catch (CieCheckerException | CMSException | IOException | CertificateException | NoSuchAlgorithmException | OperatorCreationException e ){
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
    public boolean verifyIntegrity(Path sodPath, List<Path> dgPaths) {
        Objects.requireNonNull(sodPath, "sodPath null");
        try {
            byte[] sodBytes = Files.readAllBytes(sodPath);
            List<Path> dgs = dgPaths != null ? dgPaths : Collections.emptyList();
            return verifyIntegrityCore(sodBytes, dgs);
        } catch (Exception e) {
            System.err.println("Error in verifyIntegrity: " + e.getMessage());
            return false;
        }
    }

    /**
     * esegue la logica vera e propria e propaga le eccezioni.
     * - usa ValidateUtils.decodeSodHr(...) per ottenere SodSummary (equivalente a sod_summary dello script)
     * - per ogni DG contenuto in dgFiles verifica hash
     *
     * Ritorna true solo se tutti i DG verificati combaciano.
     */
    public boolean verifyIntegrityCore(byte[] sodBytes, List<Path> dgFiles) throws Exception {
        // 1) decodifica SOD -> SodSummary (ValidateUtils contiene decodeSodHr)
        SodSummary sodSummary = decodeSodHr(sodBytes);

        // 2) identifica algoritmo di digest (OID -> nome MessageDigest)
        String hashAlgorithmName = getDigestName(sodSummary.getDgDigestAlgorithm().getAlgorithm().getId());
        if (hashAlgorithmName == null || hashAlgorithmName.isBlank()) {
            throw new CieCheckerException("Unable to determine hash algorithm from SOD");
        }

        // 3) verifica compatibilità algoritmo
        if (!COMPATIBLE_ALGOS.contains(hashAlgorithmName)) {
            throw new CieCheckerException("Unsupported hash algorithm: " + hashAlgorithmName);
        }
        System.out.println("Selected Algorithm: " + hashAlgorithmName);

        // 4) recupera map degli hash attesi
        Map<Integer, byte[]> expectedHashes = sodSummary.getDgExpectedHashes();
        if (expectedHashes == null || expectedHashes.isEmpty()) {
            throw new CieCheckerException("No expected hashes found in SOD");
        }
        System.out.println("Expected hash : " + expectedHashes);

        // 5) calcolo e verifica per ogni DG passato
        MessageDigest md = MessageDigest.getInstance(hashAlgorithmName);

        for (Path dgPath : dgFiles) {
            String fileName = dgPath.getFileName().toString();
            Integer dgNum = extractDgNumber(fileName);
            if (dgNum == null) {
                throw new CieCheckerException("Unable to extract DG number from filename: " + fileName);
            }

            // verifica esistenza e lettura file
            byte[] fileContent = Files.readAllBytes(dgPath);
            if (fileContent.length == 0) {
                throw new CieCheckerException("DG file is empty: " + dgPath);
            }

            // calcola digest
            md.reset();
            byte[] actualDigest = md.digest(fileContent);

            // expected
            byte[] expectedDigest = expectedHashes.get(dgNum);
            if (expectedDigest == null) {
                throw new CieCheckerException("No expected digest found in SOD for DG" + dgNum);
            }

            // confronto
            boolean isSameDigest = Arrays.equals(actualDigest, expectedDigest);
            System.out.printf("DG%d -> expected=%s, actual=%s, esito=%s%n",
                    dgNum,
                    bytesToHex(expectedDigest),
                    bytesToHex(actualDigest),
                    isSameDigest ? "OK" : "KO");

            if (!isSameDigest) {
                throw new CieCheckerException("DG not machtes: actualDG="+ Arrays.toString(actualDigest) +" expectedDG="+ Arrays.toString(expectedDigest));
            }
        }
        // se arrivo qui tutti i DG verificati sono ok
        return true;
    }


    /**
     * Verifica la validità della firma e della catena di fiducia del SOD
     * verify_signature.sh
     *
     * @param cieMrtd CieMrtd
     * @return boolean
     */
    @Override
    public boolean verifyDigitalSignatureMrtd(CieMrtd cieMrtd) {
        try {
            if (cieMrtd == null || cieMrtd.getSod() == null || cieMrtd.getCscaAnchor() == null || cieMrtd.getCscaAnchor().isEmpty()) {
                throw new CieCheckerException(EXC_INPUT_PARAMETER_NULL);
            }
            CMSSignedData cms = new CMSSignedData(cieMrtd.getSod());
            X509CertificateHolder holder = ValidateUtils.extractDscCertDer(cms);
            byte[] dscDer = holder.getEncoded();

            //verifica se il certificato contenuto in $DSC_DER_FILE è stato firmato da una delle autorità di certificazione presenti nel file $TRUST_BUNDLE_PEM.
            if(!ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, cieMrtd.getCscaAnchor(), new Date())) {
                System.err.println(EXC_NO_CERTIFICATE_NOT_SIGNED);
                return false;
            }
            return ValidateUtils.verifyDigitalSignature(cms);

        } catch (CMSException e) {
            System.err.println("Error during verification SOD: " + e.getMessage());
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        }
    }

}
