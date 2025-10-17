package it.pagopa.pn.ciechecker.generator.sod;

import it.pagopa.pn.ciechecker.generator.dg.CieDataGroupBuilder;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.icao.DataGroupHash;
import org.bouncycastle.asn1.icao.ICAOObjectIdentifiers;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SodMrtdBuilder {

    public enum HashAlg {
        SHA256("SHA-256");
        public final String jcaName;
        HashAlg(String n) { this.jcaName = n; }
    }

    public byte[] buildMrtdSod(byte[] dg1, byte[] dg11,
                               PrivateKey dsKey, X509Certificate dsCert) throws Exception {
        Map<Integer, byte[]> map = new LinkedHashMap<>();
        map.put(1,  digest(HashAlg.SHA256, dg1));   // DG1
        map.put(11, digest(HashAlg.SHA256, dg11));  // DG11
        return buildSignedSod(map, dsKey, dsCert);
    }

    public CieMrtd buildCieMrtd(
            String surname,
            String givenName,
            String documentNumber,
            String nationality,
            LocalDate dateOfBirth,
            char sex,
            LocalDate expiryDate,
            String codiceFiscale,
            String placeOfBirth,
            PrivateKey dsPrivateKey,
            X509Certificate dsCertificate
    ) throws Exception {

        String fullName = givenName + " " + surname;

        // DG1/DG11
        CieDataGroupBuilder dgBuilder = new CieDataGroupBuilder();

        byte[] dg1 = dgBuilder.buildDG1(
                surname,
                givenName,
                documentNumber,
                nationality,
                dateOfBirth,
                sex,
                expiryDate
        );

        byte[] dg11 = dgBuilder.buildDG11(
                fullName,
                codiceFiscale,
                dateOfBirth,
                placeOfBirth,
                null,
                null,
                null
        );

        // SOD GENERATION
        byte[] sod = buildMrtdSod(dg1, dg11, dsPrivateKey, dsCertificate);

        // CieMrtd
        CieMrtd mrtd = new CieMrtd();
        mrtd.setDg1(dg1);
        mrtd.setDg11(dg11);
        mrtd.setSod(sod);

        return mrtd;
    }

    /** Builds CMS SignedData with eContent = LDSSecurityObject (DER). */
    public byte[] buildSignedSod(Map<Integer, byte[]> dgHashMap,
                                 PrivateKey dsKey, X509Certificate dsCert) throws Exception {
        byte[] eContent = encodeLdsSecurityObject(dgHashMap);

        CMSTypedData msg = new CMSProcessableByteArray(
                ICAOObjectIdentifiers.id_icao_ldsSecurityObject, eContent
        );

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder()
                        .build("SHA256withRSA", dsKey, dsCert)
        );
        gen.addCertificate(new JcaX509CertificateHolder(dsCert));

        CMSSignedData sd = gen.generate(msg, true);
        return sd.getEncoded();
    }

    public byte[] encodeLdsSecurityObject(Map<Integer, byte[]> dgHashMap) throws IOException {
        AlgorithmIdentifier dgDigestAlgId =
                new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);

        DataGroupHash[] dghArray = dgHashMap.entrySet().stream()
                .map(e -> new DataGroupHash(e.getKey(), new DEROctetString(e.getValue())))
                .toArray(DataGroupHash[]::new);

        LDSSecurityObject lds = new LDSSecurityObject(dgDigestAlgId, dghArray);

        return lds.getEncoded(ASN1Encoding.DER);
    }

    public static byte[] digest(HashAlg alg, byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(alg.jcaName);
        return md.digest(data);
    }
}
