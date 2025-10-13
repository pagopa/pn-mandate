package it.pagopa.pn.ciechecker.utils;

import java.io.*;

import com.payneteasy.tlv.BerTag;
import com.payneteasy.tlv.BerTlv;
import com.payneteasy.tlv.BerTlvParser;
import com.payneteasy.tlv.BerTlvs;
import it.pagopa.pn.ciechecker.CieCheckerConstants;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.model.SodSummary;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.icao.DataGroupHash;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.stream.Collectors;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.*;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.util.DigestFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.Store;
import org.bouncycastle.util.encoders.Hex;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@lombok.CustomLog
public class ValidateUtils {

    private ValidateUtils() {}

    public static ResultCieChecker verifyDscAgainstTrustBundle(byte[] dscDer, Collection<X509Certificate> cscaTrustAnchors, Date atTime) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE);
        if (Objects.isNull(dscDer) || dscDer.length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_PARSING_CERTIFICATION);
        if (Objects.isNull(cscaTrustAnchors) || cscaTrustAnchors.isEmpty()) throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED);

        log.debug("Verifica se il certificato e' stato firmato da una delle autorita' di certificazione presenti nella catena di fiducia 'cscaAnchor'");
        try {
            CertificateFactory x509Cf = CertificateFactory.getInstance(X_509);

            X509Certificate dsc = (X509Certificate) x509Cf.generateCertificate(new ByteArrayInputStream(dscDer));
            CertPath path = x509Cf.generateCertPath(Collections.singletonList(dsc));

            Set<TrustAnchor> anchors = cscaTrustAnchors.stream().map(a -> new TrustAnchor(a, null)).collect(Collectors.toSet());

            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);
            if (atTime != null) params.setDate(atTime);

            CertPathValidator.getInstance(PKIX).validate(path, params); // No exception thrown = ok

            log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE, "ResultCieChecker", ResultCieChecker.OK.getValue());
            return ResultCieChecker.OK;

        } catch (CertificateException ce) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE, ce.getClass().getName() +  " - Message: " + ce.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CERTIFICATE, ce);
        }catch (CertPathValidatorException cpe) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE, cpe.getClass().getName() +  " - Message: " +cpe.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_VALIDATE_CERTIFICATE, cpe);
        }catch (NoSuchAlgorithmException nsae){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE, nsae.getClass().getName() +  " - Message: " + nsae.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SUPPORTED_CERTIFICATEPATHVALIDATOR, nsae);
        } catch ( InvalidAlgorithmParameterException ie) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE, ie.getClass().getName()  + " - Message: " + ie.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CERTPATHVALIDATOR, ie);
        } catch (Exception e){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE, e.getClass().getName() + " - Message: " + e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_EXCEPTION, e);
        }
    }


    //***********************************************
    //    nis_verify_sod_passive_auth.sh
    //************************************************

    public static X509CertificateHolder extractDscCertDer(CMSSignedData cms) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_DSC_CERT_DER);
        if(Objects.isNull(cms)) throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_CMSSIGNEDDATA);
        try {
            log.debug("Invoke extractDscCertDer() for cms signed content type OID={}", cms.getSignedContentTypeOID());
            Store<X509CertificateHolder> certStore = cms.getCertificates();

            Collection<X509CertificateHolder> matches = certStore.getMatches(null);
            log.debug("matches sixe: {}", matches.size());
            if (!matches.isEmpty()) {
                return matches.iterator().next();
            }
        }catch (Exception e){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DSC_CERT_DER, e.getClass().getName() + " - Message: " + ResultCieChecker.KO_EXC_NOTFOUND_CERTIFICATES.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_CERTIFICATES, e);
        }
        log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DSC_CERT_DER, CieCheckerException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_NOTFOUND_CERTIFICATES.getValue());
        throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_CERTIFICATES);
    }

    /**
     * Estrazione la PublicKey dal Certificato X509 - riga 64
     * @param certHolder certificato
     * @return PublicKey
     * @throws CieCheckerException ResultCieChecker.KO_EXC_PARSING_CERTIFICATION
     */
    public static PublicKey extractPublicKeyFromHolder(X509CertificateHolder certHolder) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_PUBLICKEY_FROM_HOLDER);
        if (  Objects.isNull(certHolder) ) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_PUBLICKEY_FROM_HOLDER, CieCheckerException.class.getName() + " Message: " + ResultCieChecker.KO_EXC_GENERATE_CERTIFICATE.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CERTIFICATE);
        }
        try {
            log.debug("X509CertificateHolder: {}", certHolder.getSubject().toString());
            // Per convertire X509CertificateHolder in un X509Certificate utilizzo la classe JcaX509CertificateConverter
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            //converter.setProvider(BouncyCastleProvider.PROVIDER_NAME); java.security.cert.CertificateException: Errore durante la conversione del certificato per ottenere la chiave pubblica.
            X509Certificate certificate = converter.getCertificate(certHolder);
            log.debug("SerialNumber: {}", certificate.getSerialNumber());

            // Estrae la chiave pubblica dall'oggetto X509Certificate
            return certificate.getPublicKey();

        } catch (CertificateException ce) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_PUBLICKEY_FROM_HOLDER, ce.getClass().getName() + " - Message: " + ce.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_PARSING_CERTIFICATION, ce);
        }
    }

    /**
     * Estrazione delle firme di ogni firmatario dal SignedData
     * riga 70: openssl asn1parse -inform DER -in "$SIGNED_DATA_ONLY" -strparse "$signature_offset" -out "$SIGNATURE_FILE" >/dev/null 2>&1
     * @param signedData CMSSignedData
     * @return List<byte[]> Lista di firme di ogni firmatario dal SignedData in byte[]
     * @throws CMSException exception
     */
    public static List<byte[]> extractSignaturesFromSignedData(CMSSignedData signedData) throws CMSException {

        //Ottengo lo store (contenitore) delle informazioni sui firmatari.
        SignerInformationStore signerInfos = signedData.getSignerInfos();
        //Ottiengo la collezione di tutti i firmatari.
        Collection<SignerInformation> signers = signerInfos.getSigners();
        // Verifico che ci sia almeno un firmatario.
        if (signers.isEmpty()) {
            throw new CMSException(CieCheckerConstants.EXC_NOFOUND_SIGNER);
        }

        //Crea una lista per memorizzare le firme.
        List<byte[]> signatures = new ArrayList<>();
        for (SignerInformation signerInfo : signers) {
            signatures.add(signerInfo.getSignature());
        }
        // Restituisci la lista completa delle firme.
        return signatures;
    }

    // INIT : ESTRAZIONE DEGLI HASH: CONTENT

    /**
     * Verifica che lo sha256 di hashes_octet_block sia uguale al digest estratto
     * @param cms CMSSignedData
     * @return boolean
     * @throws CieCheckerException ResultCieChecker.KO
     */
    public static boolean verifyMatchHashContent(CMSSignedData cms) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_MATCH_HASHCONTENT);
        try {
            // --- PARTE 1: ESTRAI E CALCOLA L'HASH DEI DATI FIRMATI ---
            byte[] hashSignedData = ValidateUtils.extractHashBlock(cms);
            if ( Objects.isNull(hashSignedData)  || hashSignedData.length == 0) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_MATCH_HASHCONTENT, CieCheckerException.class.getName() + " - Message: " +ResultCieChecker.KO_EXC_NO_HASH_SIGNED_DATA.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_HASH_SIGNED_DATA);
            }
            // --- PARTE 2: ESTRAI L'HASH FIRMATO (messageDigest) ---
            ASN1OctetString signedHash = ValidateUtils.extractHashSigned(cms);
            return ValidateUtils.verifyOctetStrings(hashSignedData, signedHash);
        }catch(CieCheckerException ce){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_MATCH_HASHCONTENT, CieCheckerException.class.getName() + " - Message: " + ce.getMessage());
            throw new CieCheckerException(ce.getResult(), ce);
        }catch(Exception e){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_MATCH_HASHCONTENT, e.getClass().getName() + " - Message: " + e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    /**
     * Verifica che la 1a OctetString sia identica alla 5a OctetString
     * @param firstOctetString byte[]
     * @param fiveOctetString ASN1OctetString
     * @return boolean
     * @throws CieCheckerException ResultCieChecker.KO_EXC_NO_HASH_SIGNED_DATA, KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP
     */
    public static boolean verifyOctetStrings(byte[] firstOctetString, ASN1OctetString fiveOctetString) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_OCTECTSTRINGS);
        if ( Objects.isNull(firstOctetString)  || firstOctetString.length == 0) {
            //log.error("Error in verifyOctetStrings: byte[] firstOctetString: ", firstOctetString );
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_OCTECTSTRINGS, CieCheckerException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_NO_HASH_SIGNED_DATA.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_HASH_SIGNED_DATA);
        }
        if (Objects.isNull(fiveOctetString)) {
            //log.error("Error in verifyOctetStrings: ASN1OctetString fiveOctetString is null" );
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_OCTECTSTRINGS, CieCheckerException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_NO_HASH_SIGNED_DATA.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_HASH_SIGNED_DATA);
        }

        String firstStr = calculateSha256(firstOctetString);
        String fiveStr = getHexFromOctetString(fiveOctetString);
        log.debug("calculateSha256 --> firstStr: {} - getHexFromOctetString --> fiveStr: {}", firstStr, fiveStr);
        if (firstStr.equalsIgnoreCase(fiveStr)) {
            log.debug("VERIFICA RIUSCITA: Gli hash corrispondono.");
            log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_OCTECTSTRINGS, "boolean", true);
            return true;
        } else {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_OCTECTSTRINGS, CieCheckerException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP);
        }
    }

    /**
     * Converte byte[] in String esadecimale Sha256
     * @param octetByte byte[]
     * @return String
     * @throws CieCheckerException exception
     */
    public static String calculateSha256(byte[] octetByte) throws CieCheckerException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(octetByte);
            return Hex.toHexString(hashBytes).toString().toUpperCase();
        }catch(NoSuchAlgorithmException nsae){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_CALCULATE_SHA256, nsae.getClass().getName() + " - Message: " + nsae.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_MESSAGEDIGESTSPI_SUPPORTED, nsae);
        }
    }

    /**
     * Metodo di conversione per il digest di un ASN1OctetString di BouncyCastle in una stringa
     * @param octetString  ASN1OctetString
     * @return String
     * @throws CieCheckerException exception
     */
    private static String getHexFromOctetString(ASN1OctetString octetString) throws CieCheckerException {
        if (octetString == null) {
            throw new CieCheckerException("ASN1OctetString octetString is null");
        }
        byte[] digestBytes = octetString.getOctets();
        return bytesToHex(digestBytes);
    }

    /**
     * Conversione di un byte[] in Stringa esadecimale
     * @param bytes byte[]
     * @return String
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Converte ogni byte in due cifre esadecimali
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    /**
     * Estrae la prima OCTET STRING che contiene gli hash crittografici dei dati originali
     * @param signedData CMSSignedData
     * @return byte[]
     * @throws CieCheckerException e
     */
    public static byte[] extractHashBlock(CMSSignedData signedData) throws CieCheckerException {

        CMSTypedData signedContent = signedData.getSignedContent();
        if (Objects.isNull(signedContent) || !(signedContent.getContent() instanceof byte[])) {
            log.error("Error in extractHashBlock: {}", EXC_INVALID_CMSTYPEDDATA);
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA);
        }

        return (byte[]) signedContent.getContent();
    }


    /**
     * Estrae la quinta OCTET STRING che contiene il digest (l'hash) dell'intera struttura di dati
     * @param signedData CMSSignedData
     * @return ASN1OctetString
     * @throws CieCheckerException cce
     */
    public static ASN1OctetString extractHashSigned(CMSSignedData signedData) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_HASHSIGNED);
        try {
            SignerInformationStore signers = signedData.getSignerInfos();
            if (Objects.isNull(signers) || signers.size() == 0) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_HASHSIGNED, CieCheckerException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_NO_SIGNERINFORMATIONSTORE.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNERINFORMATIONSTORE);  //"SignerInformationStore is empty");
            }

            // Prendo il primo firmatario (ce ne dovrebbe essere uno solo in questo caso)
            Collection<SignerInformation> signerCollection = signers.getSigners();
            Iterator<SignerInformation> it = signerCollection.iterator();
            SignerInformation signer = it.next();

            // Estrai il messageDigest dagli attributi firmati
            AttributeTable signedAttributes = signer.getSignedAttributes();
            Attribute messageDigestAttribute = signedAttributes.get(PKCSObjectIdentifiers.pkcs_9_at_messageDigest);

            // Estrai l'OCTET STRING che contiene il valore dell'hash
            return (ASN1OctetString) messageDigestAttribute.getAttrValues().getObjectAt(0);
        }catch(Exception e){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_HASHSIGNED, e.getClass().getName() + " - Message: " + e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_EXCEPTION, e);
        }
    }
    // END ESTRAZIONE DEGLI HASH: CONTENT


    /**
     * ESTRAZIONE DEGLI ATTRIBUTI FIRMATI (signedAttributes)
     * @param signedData  CMSSignedData
     * @return Hashtable<ASN1ObjectIdentifier, Attribute>
     * @throws CieCheckerException e
     * @throws CMSException e
     */
    public static Hashtable<ASN1ObjectIdentifier, Attribute> extractAllSignedAttributes(CMSSignedData signedData) throws CieCheckerException, CMSException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_ALLSIGNEDATTR);
        SignerInformationStore signers = signedData.getSignerInfos();
        if (signers.size() == 0) {
            throw new CMSException("SignerInformationStore is empty");
        }

        // Prendo il primo firmatario (ce ne dovrebbe essere uno solo in questo caso)
        Collection<SignerInformation> signerCollection = signers.getSigners();
        Iterator<SignerInformation> it = signerCollection.iterator();
        if (it.hasNext()) {
            SignerInformation signer = it.next();
            // Estrai gli attributi firmati
            AttributeTable signedAttributes = signer.getSignedAttributes();
            log.debug("signedAttributes.size: {}", signedAttributes.size());
            log.debug("signedAttributes.size: {}", signedAttributes.toHashtable());
            log.debug("signedAttributes : {}", signedAttributes.toASN1Structure().getAttributes());
            // Converti l'AttributeTable in una Hashtable
            if ( Objects.isNull(signer.getSignedAttributes()) ) {
                log.error("Error in extractAllSignedAttributes: " + CieCheckerException.class.getName() + " Message: " + EXC_NO_SIGNED_ATTRIBUTE);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNED_ATTRIBUTE );
            }
            return (signer.getSignedAttributes()).toHashtable();
        }
        throw new CMSException(EXC_NO_SIGNERINFORMATION);
    }

    /*
     * ANALISI DEGLI HASH DEI DATI (DataGroupHashes)
     */

    /**
     * IL DataGroupHashes è il contenuto firmato del SOD :
     * Estrazione della lista degli hash dei Data Group da un oggetto CMSSignedData
     * @param cmsData CMSSignedData
     * @return List<String>
     * @throws CieCheckerException e
     */
    public static List<String> extractDataGroupHashes(CMSSignedData cmsData) throws CieCheckerException {
        List<String> hashes = new ArrayList<>();
        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP);

        // Ottieni il contenuto firmato: il primo OCTET STRING che contiene gli hash.
        CMSTypedData signedContent = cmsData.getSignedContent();
        if (Objects.isNull(signedContent) ) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, CMSException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_NO_CMSTYPEDDATA);
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CMSTYPEDDATA);
        }
        if(!(signedContent.getContent() instanceof byte[])){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, CMSException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA);
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA);
        }
        byte[] contentBytes = (byte[]) signedContent.getContent();
        //Decodifico il contenuto binario come una struttura ASN1.
        try (ASN1InputStream asn1is = new ASN1InputStream(new ByteArrayInputStream(contentBytes))) {
            ASN1Primitive obj = asn1is.readObject();

            if (obj instanceof ASN1Sequence) {
                ASN1Sequence mainSequence = (ASN1Sequence) obj;

                ASN1Primitive hashListPrimitive = mainSequence.getObjectAt(2).toASN1Primitive();
                if (hashListPrimitive instanceof ASN1Sequence) {
                    ASN1Sequence hashSequenceList = (ASN1Sequence) hashListPrimitive;

                    //Itera su ogni elemento della sequenza (ogni Data Group Hash).
                    for (ASN1Encodable element : hashSequenceList) {
                        if (element.toASN1Primitive() instanceof ASN1Sequence) {
                            ASN1Sequence hashEntry = (ASN1Sequence) element.toASN1Primitive();

                            // Estrai il numero del Data Group e l'OCTET STRING dell'hash.
                            if (hashEntry.size() >= 2) {
                                ASN1Integer dgNumber = ASN1Integer.getInstance(hashEntry.getObjectAt(0));

                                ASN1OctetString dgHash = ASN1OctetString.getInstance(hashEntry.getObjectAt(1));
                                log.debug("Founded hash for DataGroup ASN1Integer: {} - dgNumber.toString(): {} ", dgNumber.getValue(), dgNumber);
                                log.debug("Founded hash DataGroup ASN1OctetString: {} ", Hex.toHexString(dgHash.getOctets()).toUpperCase());
                                // Aggiungi l'hash alla lista in formato esadecimale.
                                hashes.add(Hex.toHexString(dgHash.getOctets()).toUpperCase());
                            }else {
                                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, CieCheckerException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA.getValue());
                                throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA);//"Il contenuto firmato non è una sequenza di hash valida.");
                            }
                        }else {
                            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, CieCheckerException.class.getName() + " - Message: " +ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA.getValue());
                            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA);//"Il contenuto firmato non è una sequenza di hash valida.");
                        }
                    }
                }else {
                    log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, CieCheckerException.class.getName() + " - Message: " +ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA.getValue());
                    throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA);//"Il contenuto firmato non è una sequenza di hash valida.");
                }
            } else {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, CieCheckerException.class.getName() + " - Message: " + ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA);//"Il contenuto firmato non è una sequenza di hash valida.");
            }
        } catch (IOException ioe) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, ioe.getClass().getName() + " - Message: " +ioe.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_IOEXCEPTION, ioe);
        }
        log.debug("Founded DataGroup Hashes SIZE: {}", hashes.size());
        log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_DATAGROUP, "List<String>", hashes);
        return hashes;
    }

    /**
     * Estrazione e verifica della lista degli hash dei Data Group
     * @param cmsData CMSSignedData
     * @param nisSha256 byte[]
     * @return boolean
     * @throws CieCheckerException c
     */
    public static boolean verifyNisSha256FromDataGroup(CMSSignedData cmsData, byte[] nisSha256) throws CieCheckerException {

        String nisHexToCheck = calculateSha256(nisSha256);
        List<String> dataGroupList = extractDataGroupHashes(cmsData);
        if(dataGroupList.isEmpty() ) {
            log.error("Error in verifyNisSha256FromDataGroup: " + CieCheckerException.class.getName() + " - Message: " + EXC_NO_NIS_HASHES_DATAGROUP);
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_NIS_HASHES_DATAGROUP);
        }
        if (dataGroupList.contains(nisHexToCheck)) {
            return true;
        } else {
            log.error("Error in verifyNisSha256FromDataGroup: " + EXC_NO_MATCH_NIS_HASHES_DATAGROUP);
            return false;
        }
    }

    // END : ESTRAZIONE DEGLI HASH: CONTENT

    /**
     * VERIFICA FINALE DELLA FIRMA DIGITALE -
     * @param cms CMSSignedData
     * @param publicKey PublicKey
     * @return ResultCieChecker
     * @throws CieCheckerException c
     */
    public static ResultCieChecker verifySodPassiveDigitalSignature(CMSSignedData cms, PublicKey publicKey) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_SOD_PASS_DIGITAL_SIGNATURE);
        try {

            // 1. Ottieni il primo SignerInformation (presumendo che ce ne sia uno solo)
            SignerInformation signerInfo = cms.getSignerInfos().getSigners().iterator().next();
            log.debug("signerInfo.getEncryptionAlgOID(): {}", signerInfo.getEncryptionAlgOID());
            log.debug("signerInfo.getDigestAlgOID(): {} ", signerInfo.getDigestAlgOID());

            Signature verifier = Signature.getInstance(CieCheckerConstants.SHA_1_WITH_RSA, Security.getProvider(CieCheckerConstants.BOUNCY_CASTLE_PROVIDER));

            // 2. Ottieni i byte della firma
            byte[] signatureBytes = signerInfo.getSignature();
            if (Objects.isNull(signatureBytes) || signatureBytes.length == 0) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_SOD_PASS_DIGITAL_SIGNATURE, CieCheckerException.class.getName() + " - Message: " +ResultCieChecker.KO_EXC_NO_SIGNATURES_SIGNED_DATA.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNATURES_SIGNED_DATA);
            }
            // 3. Ottieni i byte degli attributi firmati (i dati originali)
            // Questo è il contenuto che è stato effettivamente firmato
            byte[] signedAttributesBytes = signerInfo.getEncodedSignedAttributes();

            verifier.initVerify(publicKey);
            verifier.update(signedAttributesBytes);
            if (verifier.verify(signatureBytes))
                return ResultCieChecker.OK;
            else {
                log.error("ResultCieChecker: {}", ResultCieChecker.KO_EXC_NOVALID_DIGITAL_SIGNATURE);
                return ResultCieChecker.KO_EXC_NOVALID_DIGITAL_SIGNATURE;
            }
        }catch (NoSuchElementException nee){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_SOD_PASS_DIGITAL_SIGNATURE, nee.getClass().getName() + " - Message: " + nee.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNERINFORMATION, nee);
        }catch (SignatureException se){
            // if this signature object is not initialized properly
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_SOD_PASS_DIGITAL_SIGNATURE, se.getClass().getName() + " - Message: " + se.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_SIGNATURE, se);
        }catch (InvalidKeyException ike){
            //se la chiave è invalida
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_SOD_PASS_DIGITAL_SIGNATURE, ike.getClass().getName() + " - Message: " +ike.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PUBLICKEY, ike);
        }catch (NoSuchAlgorithmException nae){
            //no Provider supports a Signature implementation for the specified algorithm
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_SOD_PASS_DIGITAL_SIGNATURE, nae.getClass().getName() + " - Message: " + nae.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_ALGORITHM, nae);
        }catch (IOException ioe){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_SOD_PASS_DIGITAL_SIGNATURE, ioe.getClass().getName() + " - Message: " + ioe.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_ERROR_CREATE_VERIFIER, ioe);
        }
    }

    /**
     * VERIFICA FINALE DELLA FIRMA DIGITALE -
     * @param cms CMSSignedData
     * @return boolean
     * @throws CieCheckerException c
     */
    public static ResultCieChecker verifyDigitalSignature(CMSSignedData cms) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_DIGITAL_SIGNATURE);
        log.debug("Cms signed content type OID={}", cms.getSignedContentTypeOID());
        try {
            SignerInformationStore signers = cms.getSignerInfos();
            Collection<SignerInformation> c = signers.getSigners();
            Iterator<SignerInformation> it = c.iterator();

            if (it.hasNext()) {
                SignerInformation signer = it.next();

                X509CertificateHolder certHolder = extractDscCertDer(cms);
                PublicKey pubKey = extractPublicKeyFromHolder(certHolder);

                // Crea il verificatore di firma
                JcaSimpleSignerInfoVerifierBuilder verifierBuilder = new JcaSimpleSignerInfoVerifierBuilder(); //.setProvider(BouncyCastleProvider.PROVIDER_NAME);
                verifierBuilder.setProvider(new BouncyCastleProvider());
                if (!signer.verify(verifierBuilder.build(pubKey))) {
                    log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DIGITAL_SIGNATURE, CieCheckerException.class.getName() + " - Message: " +ResultCieChecker.KO_EXC_INVALID_VERIFIER.getValue());
                    throw new CMSException(CieCheckerConstants.EXC_INVALID_VERIFIER);
                }
                log.info(LogsCostant.SUCCESSFUL_OPERATION_ON_LABEL, LogsCostant.VALIDATEUTILS_VERIFY_DIGITAL_SIGNATURE, "ResultCieChecker", ResultCieChecker.OK.getValue());
                return ResultCieChecker.OK;
            }
        }catch (OperatorCreationException oce){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DIGITAL_SIGNATURE, oce.getClass().getName() + " - Message: " +oce.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_ERROR_CREATE_VERIFIER, oce);
        }catch( CMSException cmse){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DIGITAL_SIGNATURE, cmse.getClass().getName() + " - Message: " +cmse.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA, cmse);
        }
        log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_VERIFY_DIGITAL_SIGNATURE, CieCheckerException.class.getName() + " - Message: " +ResultCieChecker.KO_EXC_NO_SIGNERINFORMATION.getValue());
        throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_SIGNERINFORMATION);
    }

    /**
     * Mappa OID dell'algoritmo (come estratto dal SOD) al nome utilizzabile da MessageDigest.
     * (Uguale a quanto usato nel test: 2.16.840.1.101.3.4.2.1 -> SHA-256, ecc.)
     */
    public static String getDigestName(String oid) {
        return switch (oid) {
            case "1.3.14.3.2.26" -> SHA_1;
            case "2.16.840.1.101.3.4.2.1" -> SHA_256;
            case "2.16.840.1.101.3.4.2.2" -> SHA_384;
            case "2.16.840.1.101.3.4.2.3" -> SHA_512;
            default -> throw new IllegalArgumentException("OID digest non supportato: " + oid);
        };
    }


    /**
     * Verifica che il digest calcolato corrisponda a quello atteso
     */
    public static boolean isVerifyDigest(MessageDigest md, byte[] dgContent, byte[] expectedDigest) throws CieCheckerException {
        if (dgContent == null || dgContent.length == 0 || expectedDigest == null) {
           throw new CieCheckerException(ResultCieChecker.KO_EXC_NOTFOUND_DIGEST_SOD);
        }
        md.reset();
        byte[] actualDigest = md.digest(dgContent);
        return Arrays.equals(actualDigest, expectedDigest);
    }


    //creazione oggetto rappresentante EF.SOD -> decode_sod_hr.sh
    public static SodSummary decodeSodHr(byte[] sodBytes) throws Exception {
        CMSSignedData cms = new CMSSignedData(sodBytes);

        String contentTypeOid = cms.getSignedContentTypeOID();
        byte[] eContent = (byte[]) Objects.requireNonNull(cms.getSignedContent()).getContent();
        LDSSecurityObject lds = LDSSecurityObject.getInstance(
                ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(eContent)));

        AlgorithmIdentifier dgDigestAlg = lds.getDigestAlgorithmIdentifier();

        LinkedHashMap<Integer, byte[]> dgMap = new LinkedHashMap<>();
        for (DataGroupHash dgh : lds.getDatagroupHash()) {
            dgMap.put(dgh.getDataGroupNumber(), dgh.getDataGroupHashValue().getOctets());
        }

        SignerInformation si = cms.getSignerInfos().getSigners().iterator().next();
        AlgorithmIdentifier sigAlg = AlgorithmIdentifier.getInstance(si.getDigestAlgorithmID());
        byte[] signature = si.getSignature();

        X509Certificate dsc = null;
        //X509CertificateHolder holder = extractDscCertDer(sodBytes);
        X509CertificateHolder holder = extractDscCertDer(cms);
        if (holder != null) {
            dsc = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(holder);
        }

        return new SodSummary(contentTypeOid, dgDigestAlg, dgMap, sigAlg, signature, dsc);
    }

/*
    public static List<X509Certificate> extractCscaAnchorFromZipPath(Path cscaAnchorZipFilePath) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR);
        log.debug("PATH CSCA: {}", cscaAnchorZipFilePath);

        try (InputStream fileInputStream = new FileInputStream(cscaAnchorZipFilePath.toFile())) {
            List<X509Certificate> x509CertList = ValidateUtils.getX509CertListFromZipFile(fileInputStream);
            if ( x509CertList.isEmpty()) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR, ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED);
            }
            log.info(LogsCostant.SUCCESSFUL_OPERATION_NO_RESULT_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR);
            return x509CertList;
        } catch (IOException ioe) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR, ioe.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED, ioe);
        }catch (Exception e ){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED, e);
        }
    }
*/

    /**
     * Estrae la lista di certificati da un archivio ZIP
     * Cerca i file con estensione .pem e li converte in X509Certificate
     * @param zipStream L'InputStream del file ZIP.
     * @return List<X509Certificate> List of X509Certificate
     * @throws CieCheckerException Se si verifica un errore durante la lettura dello ZIP.
     */
    public static List<X509Certificate> getX509CertListFromZipFile(InputStream zipStream) throws CieCheckerException {
        List<X509Certificate> x509List;
        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_GETX509CERTLIST_ZIPFILE);
        try {
            x509List = new ArrayList<>();
            ZipInputStream zis = new ZipInputStream(zipStream);
            ZipEntry entry;

            while( (entry = zis.getNextEntry()) != null) {
                log.debug("ZIS: {}" , entry.getName());
                if(entry.getName().endsWith(".pem")){
                    List<X509Certificate> pemList = ValidateUtils.loadCertificateFromPemFile(zis);
                    x509List.addAll(pemList);
                }else {
                    //entry = zis.getNextEntry();
                    CMSSignedData cms;
                    if (entry != null) {
                        cms = new CMSSignedData(zis);

                        ASN1InputStream input = new ASN1InputStream((byte[]) cms.getSignedContent().getContent());

                        ASN1Primitive p;
                        p = input.readObject();
                        ASN1Sequence seq0Lev = ASN1Sequence.getInstance(p);
                        Enumeration<ASN1Primitive> enum0Lev = seq0Lev.getObjects();
                        ASN1Integer int1Lev = (ASN1Integer) enum0Lev.nextElement();
                        ASN1Set set1Lev = (ASN1Set) enum0Lev.nextElement();
                        Enumeration<ASN1Primitive> enum1Lev = set1Lev.getObjects();

                        while (enum1Lev.hasMoreElements()) {
                            ASN1Object asn1Obj = (ASN1Object) enum1Lev.nextElement();
                            X509CertificateHolder holder = new X509CertificateHolder(asn1Obj.toASN1Primitive().getEncoded());

                            RDN rdns[] = holder.getSubject().getRDNs(ASN1ObjectIdentifier.tryFromID("2.5.4.6"));
                            if (rdns.length > 0) {
                                RDN rdn = rdns[0];
                                if (rdn != null
                                        && rdn.getFirst().getValue() != null) {
                                    String country = rdn.getFirst().getValue().toString();
                                    if (country.equals("IT")) {
                                        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);
                                        if (isSelfSigned(cert)) {
                                            x509List.add(cert);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
            zis.close();
            return x509List;
        } catch (Exception e) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_GETX509CERTLIST_ZIPFILE, e.getClass().getName() + " - Message: " + e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED, e); // File .ml non trovato nello ZIP
        }
    }

    public static boolean isSelfSigned(X509Certificate cert) {
        try {
            // Confronto Subject e Issuer
            if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                return false;
            }

            // Verifica firma con la sua stessa chiave pubblica
            PublicKey key = cert.getPublicKey();
            cert.verify(key);  // Solleva eccezione se la firma non è valida
            return true;
        } catch (Exception e) {
            // Se la verifica fallisce, non è self-signed
            return false;
        }
    }


    public static String extractCodiceFiscaleByOid(byte[] dg11Bytes) throws CieCheckerException {

        try {
            //parser TLV
            log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_CODICEFISCALE_DELEGANTE);
            BerTlvParser parser = new BerTlvParser();
            BerTlvs tlvs = parser.parse(dg11Bytes, 0, dg11Bytes.length);
            BerTag bTag = new BerTag(org.apache.commons.codec.binary.Hex.decodeHex(CieCheckerConstants.TAG_PERSONAL_NUMBER));
            BerTlv bTlv = tlvs.find(bTag);
            if (bTlv != null) {
                //log.debug("CODICE_FISCALE DELEGANTE: " + bTlv.getTextValue());
                return bTlv.getTextValue();
            } else {
                log.error("ResultCieChecker: {}", ResultCieChecker.KO_EXC_NOFOUND_CODFISCALE_DG11);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NOFOUND_CODFISCALE_DG11);
            }
        } catch (DecoderException de) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_CODICEFISCALE_DELEGANTE, de.getClass().getName() + " - Message: " + de.getMessage());
            throw new CieCheckerException( ResultCieChecker.KO_EXC_DECODER_ERROR, de);
        }
    }

    public static String parserTLVTagValue(byte[] fileBytes, String tag) throws CieCheckerException {

        try {
            //parser TLV
            log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_PARSER_TLV_TAGVALUE);
            BerTlvParser parser = new BerTlvParser();
            BerTlvs tlvs = parser.parse(fileBytes, 0, fileBytes.length);
            BerTag bTag = new BerTag(org.apache.commons.codec.binary.Hex.decodeHex(tag));
            BerTlv bTlv = tlvs.find(bTag);
            if (bTlv != null) {
                return bTlv.getTextValue();
            } else {
                if (tag.equals(CieCheckerConstants.TAG_PERSONAL_NUMBER)) {
                    log.error("ResultCieChecker: {}", ResultCieChecker.KO_EXC_NOFOUND_CODFISCALE_DG11);
                    throw new CieCheckerException(ResultCieChecker.KO_EXC_NOFOUND_CODFISCALE_DG11);
                } else if (tag.equals(CieCheckerConstants.TAG_EXPIRE_DATE)) {
                    log.error("ResultCieChecker: {}", ResultCieChecker.KO_EXC_NOFOUND_EXPIRE_DATE_DG1);
                    throw new CieCheckerException(ResultCieChecker.KO_EXC_NOFOUND_EXPIRE_DATE_DG1);
                } else {
                    log.error("ResultCieChecker: {} - TAG: {}", ResultCieChecker.KO_EXC_NOFOUND_TAG_DG, tag);
                    throw new CieCheckerException(ResultCieChecker.KO_EXC_NOFOUND_TAG_DG);
                }
            }
        } catch (DecoderException de) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_PARSER_TLV_TAGVALUE, de.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_DECODER_ERROR, de);
        }
    }

      public static List<X509Certificate> extractCscaAnchorFromZip(InputStream fileInputStream) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR_ZIP);
        try{
            List<X509Certificate> x509CertList = ValidateUtils.getX509CertListFromZipFile(fileInputStream);
            if ( x509CertList.isEmpty()) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR_ZIP, CieCheckerException.class.getName()  + " - Message: " +ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED);
            }
            log.info(LogsCostant.SUCCESSFUL_OPERATION_NO_RESULT_LABEL, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR_ZIP);
            return x509CertList;
        }catch (Exception e ){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_EXTRACT_CSCAANCHOR_ZIP, e.getClass().getName() + " - Message: " +e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED, e);
        }
    }


    public static List<X509Certificate> loadCertificateFromPemFile(InputStream fileInputStream) throws CieCheckerException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.VALIDATEUTILS_LOAD_CSCAANCHOR_PEM);
        try{
            List<X509Certificate> x509CertList = List.of(getCertificateFromPemFile(fileInputStream));
            if ( x509CertList.isEmpty()) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_LOAD_CSCAANCHOR_PEM, CieCheckerException.class.getName() + " - Message: " +ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED);
            }
            log.info(LogsCostant.SUCCESSFUL_OPERATION_NO_RESULT_LABEL, LogsCostant.VALIDATEUTILS_LOAD_CSCAANCHOR_PEM);
            return x509CertList;
        }catch (Exception e ){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.VALIDATEUTILS_LOAD_CSCAANCHOR_PEM, e.getClass().getName() + " - Message: " +e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED, e);
        }
    }

    private static X509Certificate getCertificateFromPemFile(InputStream pemFileStream)
            throws CertificateException {

            CertificateFactory factory =
                    CertificateFactory.getInstance("X.509", new BouncyCastleProvider());
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(pemFileStream);

            return certificate;
    }


    public static byte[] calculateSha1(String nonce) {
        // Ottieni l'oggetto Digest per SHA-1
        Digest sha1Digest = DigestFactory.createSHA1();
        // Converte la stringa in byte usando la codifica standard (UTF-8)
        byte[] inputBytes = nonce.getBytes(StandardCharsets.UTF_8);
        // Esegue l'hashing sui byte
        sha1Digest.update(inputBytes, 0, inputBytes.length);
        // Crea un array per contenere il risultato (20 byte per SHA-1)
        byte[] result = new byte[sha1Digest.getDigestSize()];

        // Memorizza il risultato nell'array
        sha1Digest.doFinal(result, 0);
        return result;
    }



    public static String[] extractS3Components(String s3Uri) {

        log.debug("- s3Uri: {}", s3Uri);
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
            String nameKey = null;
            String key = null;
            if (objectKey != null && objectKey.startsWith("/")) {
                objectKey = objectKey.substring(1);
                if(objectKey.lastIndexOf("/") != -1) {
                    key = objectKey.substring(0, objectKey.lastIndexOf("/") +1);
                    nameKey = objectKey.substring(objectKey.lastIndexOf("/") + 1);
                }else {
                    key = "/";
                    nameKey = objectKey;
                }
            }

            log.debug("URI di Input: " + s3Uri);
            log.debug("-------------------------------------");
            log.debug("Bucket estratto:  " + bucketName );
            log.debug("Chiave estratta: " + objectKey );
            log.debug("Nome estratta: " + nameKey );
            log.debug("Path estratta: " + key );

            return new String[]{bucketName, objectKey, nameKey, key};

        } catch (URISyntaxException e) {
            log.error("Sintax error in URI S3: {}" , e.getMessage());
            return null;
        }
    }


}
