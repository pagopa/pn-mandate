package it.pagopa.pn.ciechecker.generator.sod;


import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SodBuilder {

    public enum HashAlg {
        SHA256("SHA-256");
        public final String jcaName;
        HashAlg(String n) { this.jcaName = n; }
    }

    /** Convenienza: costruisce SOD MRTD con hash di DG1 (1) e DG11 (11) */
    public byte[] buildMrtdSod(byte[] dg1, byte[] dg11,
                               PrivateKey dsKey, X509Certificate dsCert) throws Exception {
        Map<Integer, byte[]> map = new LinkedHashMap<>();
        map.put(1, digest(HashAlg.SHA256, dg1));   // DG1
        map.put(11, digest(HashAlg.SHA256, dg11)); // DG11
        return buildSignedSod(map, dsKey, dsCert);
    }

    /** Costruisce ASN.1 { SEQUENCE OF { INTEGER dg, OCTET STRING hash } } e firma CMS (SignedData) */
    public static byte[] buildSignedSod(Map<Integer, byte[]> dgHashMap,
                                        PrivateKey dsKey, X509Certificate dsCert) throws Exception {
        byte[] eContent = encodeEContent(dgHashMap);
        CMSTypedData msg = new CMSProcessableByteArray(CMSObjectIdentifiers.data, eContent);

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(dsKey);
        gen.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                        .build(signer, dsCert));
        gen.addCertificate(new JcaX509CertificateHolder(dsCert));

        // true = eContent incapsulato nel SOD (come da prassi per EF.SOD)
        CMSSignedData sd = gen.generate(msg, true);
        return sd.getEncoded();
    }

    /** Codifica ASN.1 dell’eContent: SEQUENCE OF { INTEGER, OCTET STRING } */
    public static byte[] encodeEContent(Map<Integer, byte[]> dgHashMap) throws Exception {
        ASN1EncodableVector root = new ASN1EncodableVector();
        for (Map.Entry<Integer, byte[]> e : dgHashMap.entrySet()) {
            ASN1EncodableVector item = new ASN1EncodableVector();
            item.add(new ASN1Integer(e.getKey()));
            item.add(new DEROctetString(e.getValue()));
            root.add(new DERSequence(item));
        }
        return new DERSequence(root).getEncoded(ASN1Encoding.DER);
    }

    /** Estrae l’eContent (DER) dal CMS SignedData e ritorna la mappa dg→hash */
    public Map<Integer, byte[]> parseEContentFromSod(byte[] sodBytes) throws Exception {
        CMSSignedData sd = new CMSSignedData(sodBytes);
        CMSProcessable p = sd.getSignedContent();
        if (p == null) throw new IllegalStateException("SOD without encapsulated eContent");
        byte[] eContent = (byte[]) p.getContent();
        ASN1Sequence seq = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(eContent));
        Map<Integer, byte[]> out = new LinkedHashMap<>();
        for (int i = 0; i < seq.size(); i++) {
            ASN1Sequence item = ASN1Sequence.getInstance(seq.getObjectAt(i));
            int dgNum = ASN1Integer.getInstance(item.getObjectAt(0)).getValue().intValue();
            byte[] hash = DEROctetString.getInstance(item.getObjectAt(1)).getOctets();
            out.put(dgNum, hash);
        }
        return out;
    }

    public static byte[] digest(HashAlg alg, byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance(alg.jcaName);
        return md.digest(data);
    }
}