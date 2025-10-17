package it.pagopa.pn.ciechecker.generator.ias;

import it.pagopa.pn.ciechecker.generator.model.CertAndKey;
import it.pagopa.pn.ciechecker.generator.sod.SodBuilder;
import it.pagopa.pn.ciechecker.model.CieIas;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.icao.DataGroupHash;
import org.bouncycastle.asn1.icao.ICAOObjectIdentifiers;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedData;

import java.util.Map;

public class NisBuilder {

    public static final int DEFAULT_NIS_LEN = 12;
    private final SecureRandom rng;


    public NisBuilder() {
        this.rng = new SecureRandom();
    }

    public NisBuilder(SecureRandom rng) {
        this.rng = rng;
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
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        dgHashMap.put(1, sha256.digest(nis));
        dgHashMap.put(5, sha256.digest(iasPublicKeyDer));
        return buildSignedSod(dgHashMap, privateKey, cscaCert);
    }

    public static byte[] buildSignedSod(Map<Integer, byte[]> dgHashMap,
                                 PrivateKey dsKey, X509Certificate dsCert) throws Exception {
        byte[] eContent = encodeLdsSecurityObject(dgHashMap);

        CMSTypedData msg = new CMSProcessableByteArray(
                ICAOObjectIdentifiers.id_icao_ldsSecurityObject, eContent
        );

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        JcaSignerInfoGeneratorBuilder signerInfoBuilder =
                new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build());

        signerInfoBuilder.setSignedAttributeGenerator(new DefaultSignedAttributeTableGenerator());

        gen.addSignerInfoGenerator(
                signerInfoBuilder.build(new JcaContentSignerBuilder("SHA1withRSA").build(dsKey), dsCert)
        );

        gen.addCertificate(new JcaX509CertificateHolder(dsCert));
        CMSSignedData sd = gen.generate(msg, true);
        return sd.getEncoded();
    }

    public static byte[] encodeLdsSecurityObject(Map<Integer, byte[]> dgHashMap) throws Exception {
        AlgorithmIdentifier dgDigestAlgId =
                new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
        DataGroupHash[] dghArray = dgHashMap.entrySet().stream()
                .map(e -> new DataGroupHash(e.getKey(), new DEROctetString(e.getValue())))
                .toArray(DataGroupHash[]::new);
        LDSSecurityObject lds = new LDSSecurityObject(dgDigestAlgId, dghArray);
        return lds.getEncoded(ASN1Encoding.DER);
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

    private byte[] digestSHA256(byte[] input) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        return md.digest(input);
    }

}
