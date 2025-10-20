package it.pagopa.pn.ciechecker.generator.challenge;

import it.pagopa.pn.ciechecker.CieCheckerConstants;
import it.pagopa.pn.ciechecker.model.CieValidationData;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;


public class ChallengeResponseBuilder {

    private CieValidationData cieValidationData;

    ChallengeResponseBuilder(CieValidationData cieValidationData){
        this.cieValidationData = cieValidationData;
    }

    //Crea e setta in CieValidationData il SignedNonce
    public CieValidationData generateSignedNonce(String nonce, X509Certificate cert, PrivateKey privateKey) throws Exception {

        byte[] nonceByte = nonce.getBytes(StandardCharsets.UTF_8);
        byte[] response = generateRsaSignature(nonceByte, privateKey);
        this.cieValidationData.setSignedNonce(response);
        return this.cieValidationData;
    }

    public byte[] generateRsaSignature(byte[] nonce, PrivateKey privateKey)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {

        Signature signer = Signature.getInstance(CieCheckerConstants.SHA_1_WITH_RSA); //SHA_256_WITH_RSA

        signer.initSign(privateKey);
        signer.update(nonce); // Firma sull'HASH del payloadToSign

        return signer.sign();
    }

}
