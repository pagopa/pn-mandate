package it.pagopa.pn.ciechecker.model;

import it.pagopa.pn.ciechecker.CieChecker;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

public class CieCheckerImpl implements CieChecker {

    @Override
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

    }

    @Override
    public boolean validateMandate(CieValidationData data) {
        return true;
    }

    public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey, byte[] challenge) throws  CryptoException {
        RSAEngine engine = new RSAEngine();
        PKCS1Encoding engine2 = new PKCS1Encoding(engine);
        RSAKeyParameters publicKey = extractPublicKeyFromSignature(pubKey);
        engine2.init(false, publicKey);

        byte[] recovered = engine2.processBlock(signature, 0, signature.length);
        return Arrays.equals(recovered, challenge);

    }

    private RSAKeyParameters extractPublicKeyFromSignature(byte[] pubKey) {
        RSAPublicKey pkcs1PublicKey = RSAPublicKey.getInstance(pubKey);
        BigInteger modulus = pkcs1PublicKey.getModulus();
        BigInteger publicExponent = pkcs1PublicKey.getPublicExponent();
        return new RSAKeyParameters(false,modulus, publicExponent);
    }


}
