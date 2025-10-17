package it.pagopa.pn.ciechecker.generator.challenge;

import it.pagopa.pn.ciechecker.CieCheckerConstants;
import it.pagopa.pn.ciechecker.model.CieValidationData;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.X509Certificate;


public class ChallengeResponseBuilder {

    private CieValidationData cieValidationData;

    ChallengeResponseBuilder(CieValidationData cieValidationData){
        this.cieValidationData = cieValidationData;
    }

    public CieValidationData setSignedNonce(byte[] nonce, X509Certificate cert, PrivateKey privateKey) throws Exception {

        byte[] response = calculateResponseSignature(nonce, cert, privateKey);
        this.cieValidationData.setSignedNonce(response);
        return this.cieValidationData;
    }
    //
    public byte[] calculateResponseSignature(byte[] nonce, X509Certificate cert, PrivateKey privateKey) throws Exception {

        byte[] serialNumberByte = getSerialNumber(cert);
        byte[] publicKeysHash = calculatePublicKeysHash();
        int totalLength = nonce.length + publicKeysHash.length + serialNumberByte.length;

        // 2. COSTRUZIONE DEL PAYLOAD
        ByteBuffer payloadBuffer = ByteBuffer.allocate(totalLength);

        // Ordine di concatenazione
        payloadBuffer.put(nonce);
        payloadBuffer.put(publicKeysHash);
        payloadBuffer.put(serialNumberByte);
        byte[] payloadToSign = payloadBuffer.array();

        // 3. FIRMA: Passa il payload completo alla funzione di firma (Fase 2)
        return generateRsaSignature(payloadToSign, privateKey);
    }

    private byte[] getSerialNumber(X509Certificate cert){

        BigInteger serialNumber = cert.getSerialNumber();
        return serialNumber.toByteArray();
    }

    private byte[] generateRsaSignature(byte[] dataToSign, PrivateKey privateKey)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {

        Signature signer = Signature.getInstance(CieCheckerConstants.SHA_256_WITH_RSA);

        signer.initSign(privateKey);
        signer.update(dataToSign); // Firma sull'HASH del payloadToSign

        return signer.sign();
    }


    private byte[] calculatePublicKeysHash() throws Exception {

        //@TODO
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest();
    }

}
