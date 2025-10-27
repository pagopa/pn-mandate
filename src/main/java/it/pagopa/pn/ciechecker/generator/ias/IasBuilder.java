package it.pagopa.pn.ciechecker.generator.ias;


import it.pagopa.pn.ciechecker.model.CieIas;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.icao.DataGroupHash;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedData;

import java.util.Map;

public class NisBuilder {

    public static final int DEFAULT_NIS_LEN = 12;
    private final SecureRandom rng;
    private static final String DIGITS = "0123456789";

    public NisBuilder() {
        this.rng = new SecureRandom();
    }

    public NisBuilder(SecureRandom rng) {
        this.rng = rng;
    }

    public String generateNumeric(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be > 0");
        }
        Random rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(DIGITS.charAt(rng.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }


    public byte[] generateRandom() {
        return generateRandom(DEFAULT_NIS_LEN);
    }

    public byte[] generateRandom(int length) {
        if (length <= 0 || length > 64) {
            throw new IllegalArgumentException("NIS length must be 1..64");
        }
        byte[] out = new byte[length];
        rng.nextBytes(out);
        return out;
    }

    public byte[] generateFromSeed(String... parts) {
        return generateFromSeed(DEFAULT_NIS_LEN, parts);
    }

    public byte[] generateFromSeed(int length, String... parts) {
        if (length <= 0 || length > 64) {
            throw new IllegalArgumentException("NIS length must be 1..64");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                if (p != null) {
                    md.update(p.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
                    md.update((byte) 0x00); // separatore
                }
            }
            byte[] hash = md.digest();
            return Arrays.copyOf(hash, length);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute seeded NIS", e);
        }
    }

    public static boolean isValid(byte[] nis) {
        return nis != null && nis.length >= 8 && nis.length <= 64;
    }

    public static ByteBuffer asReadOnlyBuffer(byte[] nis) {
        return ByteBuffer.wrap(nis).asReadOnlyBuffer();
    }


    public static CieIas createCieIas(byte[] nis, byte[] iasPublicKeyDer,
                               PrivateKey dsKey, X509Certificate caCert) throws Exception {
        CieIas cieIas = new CieIas();
        cieIas.setNis(nis);
        cieIas.setSod(buildIasSodWithSodBuilder(nis, iasPublicKeyDer, caCert, dsKey));
        cieIas.setPublicKey(iasPublicKeyDer);
        return cieIas;
    }

    public static byte[] buildIasSodWithSodBuilder(
            byte[] nis,
            byte[] iasPublicKeyDer,
            X509Certificate cscaCert, PrivateKey privateKey
    ) throws Exception {

        Map<Integer, byte[]> dgHashMap = new LinkedHashMap<>();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-512");
        dgHashMap.put(1, sha256.digest(nis));
        dgHashMap.put(5, sha256.digest(iasPublicKeyDer));
        return buildSignedSod(dgHashMap, privateKey, cscaCert);
    }

    public static byte[] buildSignedSod(Map<Integer, byte[]> dgHashMap,
                                 PrivateKey dsKey, X509Certificate dsCert) throws Exception {

    // DataGroupHash sequence
    ASN1EncodableVector dgHashVector = new ASN1EncodableVector();
    for (Map.Entry<Integer, byte[]> entry : dgHashMap.entrySet()) {
        ASN1EncodableVector dgEntry = new ASN1EncodableVector();
        dgEntry.add(new ASN1Integer(entry.getKey()));
        dgEntry.add(new DEROctetString(entry.getValue()));
        dgHashVector.add(new DERSequence(dgEntry));
    }

    DataGroupHash[] dghArray = dgHashMap.entrySet().stream()
        .map(e -> new DataGroupHash(e.getKey(), new DEROctetString(e.getValue())))
        .toArray(DataGroupHash[]::new);
    //LDSSecurityObject
    LDSSecurityObject ldsSO = new LDSSecurityObject(
        new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
        dghArray
    );
    
    // Encode LDSSecurityObject
    byte[] ldsSOBytes = ldsSO.getEncoded("DER");
    CMSTypedData cmsData = new CMSProcessableByteArray(CMSObjectIdentifiers.data, ldsSOBytes);

    // CMS Signature
    ContentSigner contentSigner = new JcaContentSignerBuilder("SHA512withRSA")
        .setProvider(new BouncyCastleProvider()).build(dsKey);

    CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
    generator.addSignerInfoGenerator(
        new JcaSignerInfoGeneratorBuilder(
            new JcaDigestCalculatorProviderBuilder().setProvider(new BouncyCastleProvider()).build()
        ).build(contentSigner, dsCert)
    );

    Store certs = new JcaCertStore(Collections.singletonList(dsCert));
    generator.addCertificates(certs);

    CMSSignedData signedData = generator.generate(cmsData, true);
    
    byte [] sod = new byte[signedData.getEncoded().length+4];
    System.arraycopy(signedData.getEncoded(), 0, sod, 4, signedData.getEncoded().length);
    return sod;
    
}

//creato e usato solo per stampare la struttura degli attributi
    public static void debugAsn1Structure(byte[] eContent) {
        try {
            ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(eContent));
            ASN1Primitive primitive = asn1InputStream.readObject();
            asn1InputStream.close();

            if (primitive instanceof ASN1Sequence) {
                ASN1Sequence sequence = (ASN1Sequence) primitive;
                System.out.println("Root ASN1Sequence size: " + sequence.size());
                int i = 0;
                for (ASN1Encodable obj : sequence) {
                    System.out.println(" Element #" + i + " type: " + obj.getClass().getSimpleName());
                    System.out.println("  Content: " + obj);
                    i++;
                }
            } else {
                System.out.println("Root is not a sequence, type: " + primitive.getClass().getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
