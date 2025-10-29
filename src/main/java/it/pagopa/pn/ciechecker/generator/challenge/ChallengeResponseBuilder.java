package it.pagopa.pn.ciechecker.generator.challenge;

import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;

import java.nio.charset.StandardCharsets;
import java.security.*;


public class ChallengeResponseBuilder {

    public static byte[] signNonce(String nonce, PrivateKey privateKey) throws Exception {
        byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);

        AsymmetricKeyParameter privParam = PrivateKeyFactory.createKey(privateKey.getEncoded());

        PKCS1Encoding signer = new PKCS1Encoding(new RSAEngine());
        signer.init(true, privParam);

        return signer.processBlock(nonceBytes, 0, nonceBytes.length);
    }

}
