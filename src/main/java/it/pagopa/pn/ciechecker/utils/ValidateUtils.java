package it.pagopa.pn.ciechecker.utils;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.internal.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

public class ValidateUtils {

    private static final String EXPECTED_SIGNED_ATTRS_HEX="3148301506092A864886F70D01090331080606678108010101302F06092A864886F70D01090431220420C9002855CB7A5D366DB2CD6CCD6E148B7F8265E765C520ACC88855C2F3338FEB";
    private static final String EXPECTED_SIGNATURE_HEX="68080B83AA856FC3AF1F9A1D897011526130AE85E236862890970D54746535DF3328AD0638C90584B0DB0A466C86F9A65049FBC830E34499FD54107536204DE029A575F5D97E1F1DFF604B560A97465E2B5F105BFD97B0C86EB492E960F929DA298D6FB87637051B37980C8AC4737FBFE3C37993A41FDC2749DA2EB3B21B51A2FE66979D2258FB161F8BB1DFE8BCFBFC55E50E5D62F3F4FDCB2BF880B60D63EC411809C813CC2F9DA24C03B6A45B15D3AECDA1047FAB8806029A54D70A4E9A4E3DE888899D2A6762B073C91ACBD2C39A27105CF6C24CF78352C115A18E97E9A2D29164177DB03C6F3D90F03B7D842DF31B0FE9A36CA361DD722B5AFD01EA10BD";

    private ValidateUtils() {}

    public static X509CertificateHolder extractDscCertDer(byte[] signedDataPkcs7) throws CMSException {
        CMSSignedData cms = new CMSSignedData(signedDataPkcs7);

        Store<X509CertificateHolder> certStore = cms.getCertificates();

        Collection<X509CertificateHolder> matches = certStore.getMatches(null);
        if (!matches.isEmpty()) {
            return matches.iterator().next();
        }

        throw new CieCheckerException("No certificates found in PKCS7");
    }

    //***********************************************
    //    nis_verify_sod_passive_auth.sh
    //************************************************


    public static X509CertificateHolder extractDscCertDer(CMSSignedData cms) throws CMSException {

        Store<X509CertificateHolder> certStore = cms.getCertificates();

        Collection<X509CertificateHolder> matches = certStore.getMatches(null);
        if (!matches.isEmpty()) {
            return matches.iterator().next();
        }

        throw new CieCheckerException("No certificates found in PKCS7");
    }

    /**
     * Estrae la PublicKey dal Certificato X509 - riga 64
     *
     * @param certHolder certificato
     * @return PublicKey
     * @throws CertificateException exception
     * @throws IllegalArgumentException argomento non valido
     */
    public static PublicKey extractPublicKeyFromHolder(X509CertificateHolder certHolder) throws CieCheckerException, CertificateException {
        if (certHolder == null) {
            throw new CieCheckerException("X509CertificateHolder is null");
        }
        try {
            // Per convertire X509CertificateHolder in un X509Certificate utilizzo la classe JcaX509CertificateConverter
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            //converter.setProvider(BouncyCastleProvider.PROVIDER_NAME); java.security.cert.CertificateException: Errore durante la conversione del certificato per ottenere la chiave pubblica.
            X509Certificate certificate = converter.getCertificate(certHolder);
            //System.out.println("SerialNumber: " + certificate.getSerialNumber());

            // Estrae la chiave pubblica dall'oggetto X509Certificate
            return certificate.getPublicKey();
        } catch (CertificateException e) {
            throw new CertificateException("Errore durante la conversione del certificato per ottenere la chiave pubblica.", e);
        }
    }

    /**
     * Estrazione delle firme di ogni firmatario dal SignedData
     * riga 70: openssl asn1parse -inform DER -in "$SIGNED_DATA_ONLY" -strparse "$signature_offset" -out "$SIGNATURE_FILE" >/dev/null 2>&1
     *
     * @param signedData CMSSignedData
     * @return Lista di firme di ogni firmatario dal SignedData in byte[]
     * @throws CMSException exception
     */
    public static List<byte[]> extractSignaturesFromSignedData(CMSSignedData signedData) throws CMSException {

        //Ottengo lo store (contenitore) delle informazioni sui firmatari.
        SignerInformationStore signerInfos = signedData.getSignerInfos();
        //Ottiengo la collezione di tutti i firmatari.
        Collection<SignerInformation> signers = signerInfos.getSigners();
        // Verifico che ci sia almeno un firmatario.
        if (signers.isEmpty()) {
            throw new CMSException("Nessun firmatario trovato nella struttura SignedData.");
        }

        //Crea una lista per memorizzare le firme.
        List<byte[]> signatures = new ArrayList<>();
        for (SignerInformation signerInfo : signers) {
            signatures.add(signerInfo.getSignature());
        }
        // Restituisci la lista completa delle firme.
        if (signatures.isEmpty()) {
            throw new CMSException("Nessuna firma digitale trovata nella struttura SignedData.");
        }

        return signatures;
    }

    // INIT : ESTRAZIONE DEGLI HASH: CONTENT

    //Verifica che la 1a OctetString sia identica alla 5a OctetString
    public static boolean verifyOctetStrings(byte[] firstOctetString, ASN1OctetString fiveOctetString) throws NoSuchAlgorithmException {

        String firstStr = calculateSha256(firstOctetString);
        String fiveStr = getHexFromOctetString(fiveOctetString);
        System.out.println("calculateSha256 --> firstStr: "+ firstStr + " - getHexFromOctetString --> fiveStr: " + fiveStr);
        if (firstStr.equalsIgnoreCase(fiveStr)) {
            System.out.println("VERIFICA RIUSCITA: Gli hash corrispondono.");
            return true;
        } else {
            System.out.println("VERIFICA FALLITA: Gli hash non corrispondono.");
            return false;
        }
    }

    public static String calculateSha256(byte[] octetByte) throws CieCheckerException, NoSuchAlgorithmException{
        if (octetByte == null) {
            throw new CieCheckerException("byte[] octetByte is null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(octetByte);
            return Hex.toHexString(hashBytes).toString().toUpperCase(); //bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw e;
        }
    }

    // Metodo di conversione per il digest di un ASN1OctetString di BouncyCastle in una stringa
    private static String getHexFromOctetString(ASN1OctetString octetString) throws CieCheckerException{
        if (octetString == null) {
            throw new CieCheckerException("ASN1OctetString octetString is null");
        }
        byte[] digestBytes = octetString.getOctets();
        return bytesToHex(digestBytes);
    }

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


    // Estrae la prima OCTET STRING che contiene gli hash crittografici dei dati originali
    public static byte[] extractHashBlock(CMSSignedData signedData) throws CieCheckerException, CMSException {
        if (signedData == null) {
            throw new CieCheckerException("L'oggetto CMSSignedData e' nullo");
        }
        CMSTypedData signedContent = signedData.getSignedContent();
        if (signedContent == null || !(signedContent.getContent() instanceof byte[])) {
            throw new CMSException("Il contenuto firmato e' nullo");
        }

        return (byte[]) signedContent.getContent();
    }

    //Estrae la quinta OCTET STRING che contiene il digest (l'hash) dell'intera struttura di dati
    public static ASN1OctetString extractHashSigned(CMSSignedData signedData) throws CMSException{
        SignerInformationStore signers = signedData.getSignerInfos();
        if (signers.size() == 0) {
            throw new CMSException("SignerInformationStore is empty");
        }

        // Prendo il primo firmatario (ce ne dovrebbe essere uno solo in questo caso)
        Collection<SignerInformation> signerCollection = signers.getSigners();
        Iterator<SignerInformation> it = signerCollection.iterator();
        SignerInformation signer = it.next();

        // Estrai il messageDigest dagli attributi firmati
        AttributeTable signedAttributes = signer.getSignedAttributes();
        Attribute messageDigestAttribute = signedAttributes.get(PKCSObjectIdentifiers.pkcs_9_at_messageDigest);

        // Estrai l'OCTET STRING che contiene il valore dell'hash
        ASN1OctetString messageDigestOctetString = (ASN1OctetString) messageDigestAttribute.getAttrValues().getObjectAt(0);

        return messageDigestOctetString;
    }


    //ESTRAZIONE DEGLI ATTRIBUTI FIRMATI (signedAttributes)
    public static Hashtable<ASN1ObjectIdentifier, Attribute> extractAllSignedAttributes (CMSSignedData signedData) throws CMSException{
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
            System.out.println("signedAttributes.size: " + signedAttributes.size());
            System.out.println("signedAttributes.size: " + signedAttributes.toHashtable());
            //System.out.println("signedAttributes : " + signedAttributes.toASN1Structure().getAttributes());
            // Converti l'AttributeTable in una Hashtable
            Hashtable<ASN1ObjectIdentifier, Attribute> attrTable = signedAttributes.toHashtable();

            return attrTable;
        }
        throw new CMSException("Il firmatario è null");
    }

    /*
     * ANALISI DEGLI HASH DEI DATI (DataGroupHashes)
     */

    //IL DataGroupHashes è il contenuto firmato del SOD
    // Il metodo estrae la lista degli hash dei Data Group da un oggetto CMSSignedData
    public static List<String> extractDataGroupHashes(CMSSignedData cmsData) throws  CMSException, IOException {
        List<String> hashes = new ArrayList<>();

        // Ottieni il contenuto firmato: il primo OCTET STRING che contiene gli hash.
        CMSTypedData signedContent = cmsData.getSignedContent();
        if (signedContent == null || !(signedContent.getContent() instanceof byte[])) {
            throw new CMSException("Contenuto firmato non valido o non disponibile.");
        }

        byte[] contentBytes = (byte[]) signedContent.getContent();
        //Decodifico il contenuto binario come una struttura ASN1.
        try (ASN1InputStream asn1is = new ASN1InputStream(new ByteArrayInputStream(contentBytes))) {
            ASN1Primitive obj = asn1is.readObject();

            if (obj instanceof ASN1Sequence) {
                ASN1Sequence mainSequence = (ASN1Sequence) obj;

                ASN1Primitive hashListPrimitive = mainSequence.getObjectAt(2).toASN1Primitive();
                if(hashListPrimitive instanceof ASN1Sequence) {
                    ASN1Sequence hashSequenceList = (ASN1Sequence) hashListPrimitive;

                    //Itera su ogni elemento della sequenza (ogni Data Group Hash).
                    for (ASN1Encodable element : hashSequenceList) {
                        if (element.toASN1Primitive() instanceof ASN1Sequence) {
                            ASN1Sequence hashEntry = (ASN1Sequence) element.toASN1Primitive();

                            // Estrai il numero del Data Group e l'OCTET STRING dell'hash.
                            if (hashEntry.size() >= 2) {
                                ASN1Integer dgNumber = ASN1Integer.getInstance(hashEntry.getObjectAt(0));

                                ASN1OctetString dgHash = ASN1OctetString.getInstance(hashEntry.getObjectAt(1));
                                System.out.println("Trovato hash per il Data Group " + dgNumber.getValue() + " - dgNumber.toString(): " + dgNumber.toString());
                                System.out.println("Trovato hash  " + Hex.toHexString(dgHash.getOctets()).toUpperCase());
                                // Aggiungi l'hash alla lista in formato esadecimale.
                                hashes.add(Hex.toHexString(dgHash.getOctets()).toUpperCase());
                            }
                        }
                    }
                }
            } else {
                throw new IOException("Il contenuto firmato non è una sequenza di hash valida.");
            }
        }
        System.out.println("Trovato hashes SIZE: " + hashes.size());
        return hashes;
    }

    public static boolean verifyNisSha256FromDataGroup(CMSSignedData cmsData, String nisSha256) throws CieCheckerException, IOException, CMSException {
        if (cmsData == null || nisSha256 == null) {
            throw new CieCheckerException("Input parameters null: CMSSignedData is " + cmsData + " - String is " + nisSha256);
        }
        List<String> dataGroupList = extractDataGroupHashes(cmsData);
        if(dataGroupList == null)
            throw new CMSException("List<String> dataGroupList is NULL");
        if(dataGroupList.contains(nisSha256)){
            return true;
        }else{
            return false;
        }
    }

    public static boolean verifyNisPublicKeyFromDataGroup(CMSSignedData cmsData, String nisSha256PublicKey) throws CieCheckerException, IOException, CMSException {
        if (cmsData == null || nisSha256PublicKey == null) {
            throw new CieCheckerException("Input parameters NULL: CMSSignedData is " + cmsData + " - String is " + nisSha256PublicKey);
        }
        List<String> dataGroupList = extractDataGroupHashes(cmsData);
        if(dataGroupList == null)
            throw new CMSException("List<String> dataGroupList is NULL");
        if(dataGroupList.contains(nisSha256PublicKey)){
            return true;
        }else{
            return false;
        }
    }

    public static boolean verifyDataGroupHashes(CMSSignedData cmsData, String nisSha256, String nisSha256PublicKey) throws CieCheckerException, IOException, CMSException {
        if (cmsData == null) {
            throw new CieCheckerException("L'oggetto CMSSignedData e' nullo");
        }
        if(!ValidateUtils.verifyNisSha256FromDataGroup(cmsData, nisSha256))
            return false;
        if(!ValidateUtils.verifyNisPublicKeyFromDataGroup(cmsData, nisSha256PublicKey))
            return false;
        return true;
    }
    // END : ESTRAZIONE DEGLI HASH: CONTENT

    /*
    * PASSO 2: MANIPOLAZIONE DEL TAG PER LA VERIFICA DELLA FIRMA - Codifica da tag contestuale implicito [0] IMPLICIT a Set
    * 0x30: Rappresenta una SEQUENCE.
    * 0x31: Rappresenta un SET (o SET OF).
    * 0xA0: Rappresenta un tag contestuale implicito [0] IMPLICIT per una classe di dati specifica.
    * Quando viene richiesto l'array di byte con getEncoded(), BouncyCastle formatta correttamente aggiungendo il tag 0x31 e la lunghezza,
    * in modo che l'output sia pronto per l'algoritmo di hash SHA-256.
    */

    /*
    *  PASSO 3: VERIFICA FINALE DELLA FIRMA DIGITALE
    */
    //if [ "$ACTUAL_SIGNED_ATTRS_HEX" = "$EXPECTED_SIGNED_ATTRS_HEX" ]; then
    public static boolean veryfySignedAttrIsSet (CMSSignedData signedData) throws CMSException, IOException {
        if (signedData == null) {
            throw new CieCheckerException("L'oggetto CMSSignedData e' nullo");
        }
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
            // Ottieni l'ASN1Primitive che rappresenta l'AttributeTable (una sequenza)
            ASN1Primitive primitive = signedAttributes.toASN1Structure().toASN1Primitive();

            // Verifica che non sia una sequenza: ASN1Set
            if (!(primitive instanceof ASN1Set)) {
                throw new IOException("L'AttributeTable non è una sequenza ASN.1");
            }

            // Converto [0] IMPLICIT in SET, che è equivalente a cambiare il tag da A0 a 31 getEncoded()
            ASN1Set signedAttributesSet = (ASN1Set) primitive;
            byte[] signedAttributesSetByte = signedAttributesSet.getEncoded();
            String signedAttributesSetToHex = ValidateUtils.bytesToHex(signedAttributesSetByte);
            System.out.println("signedAttributesSetToHex: " + signedAttributesSetToHex);

            if(EXPECTED_SIGNED_ATTRS_HEX.equals(signedAttributesSetToHex))
                return true;
            else
                return false;
        }
        throw new CMSException("SignerInformation is null");
    }

    //if [ "$ACTUAL_SIGNATURE_HEX" = "$EXPECTED_SIGNATURE_HEX" ]; then
    public static boolean veryfySignatures (CMSSignedData signedData) throws CMSException {
        if (signedData == null) {
            throw new CieCheckerException("L'oggetto CMSSignedData e' nullo");
        }
        List<byte[]> signatures = extractSignaturesFromSignedData(signedData);
        List<String> signaturesHexStrings = decodeListByteIntoListString(signatures);
        if(signaturesHexStrings.contains(EXPECTED_SIGNATURE_HEX))
            return true;
        else
            return false;
    }

    private static List<String> decodeListByteIntoListString(List<byte[]> byteArrays) throws CMSException {
        List<String> hexStrings = new ArrayList<>();
        for (byte[] byteArray : byteArrays) {
            if (byteArray != null) {
                hexStrings.add(Hex.toHexString(byteArray).toUpperCase());
            }
        }
        return hexStrings;
    }


    // openssl dgst -sha1 -verify "$PUBLIC_KEY_FILE" -signature "$SIGNATURE_FILE" "$SIGNED_ATTRS_SET" >/dev/null 2>&1;
    public static boolean verifyDigitalSignature(byte[] sodIasByteArray) throws CieCheckerException, CMSException, CertificateException, OperatorCreationException {
        if (sodIasByteArray == null) {
            throw new CieCheckerException("byte[] sodIasByteArray is null");
        }
        CMSSignedData cmsData = new CMSSignedData(sodIasByteArray);

        SignerInformationStore signers = cmsData.getSignerInfos();
        Collection<SignerInformation> c = signers.getSigners();
        Iterator<SignerInformation> it = c.iterator();

        if (it.hasNext()) {
            SignerInformation signer = it.next();

            X509CertificateHolder certHolder = extractDscCertDer(cmsData);
            PublicKey pubKey = extractPublicKeyFromHolder(certHolder);

            // Crea il verificatore di firma
            //JcaSimpleSignerInfoVerifierBuilder verifierBuilder = new JcaSimpleSignerInfoVerifierBuilder();
            JcaSimpleSignerInfoVerifierBuilder verifierBuilder = new JcaSimpleSignerInfoVerifierBuilder(); //.setProvider(BouncyCastleProvider.PROVIDER_NAME);
            verifierBuilder.setProvider(new BouncyCastleProvider());
            return signer.verify(verifierBuilder.build(pubKey));
        }
        throw new CMSException("SignerInformation is null");
    }


}
