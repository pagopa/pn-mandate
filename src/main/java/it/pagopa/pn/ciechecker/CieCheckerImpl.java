package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.model.CieValidationData;

public class CieCheckerImpl implements CieChecker {

    @Override
    public void init() {
    }

    @Override
    public boolean validateMandate(CieValidationData data) {
        return false;
    }

    @Override
    public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey, byte[] nis) {
        return false;
    }

    @Override
    /**
     * Effettua la verifica completa del Document Security Object (SOD) della CIE
     * nis_verify_sod_passive_auth.sh
     *
     * @param cieIas CieIas
     * @return boolean
     */
    public boolean verificationSodCie(CieIas cieIas) {

        try {
            if (cieIas == null || cieIas.getSod() == null || cieIas.getNis() == null ) {
                throw new CieCheckerException("SOD or NIS is null");
            }
            CMSSignedData cms = new CMSSignedData(cieIas.getSod());

            /*****************************************************
             ** PASSO 1 - ANALISI E ESTRAZIONE DEI COMPONENTI
             *****************************************************/
            // - Estrazione del certificato DSC
            X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);
            // - Estrazione della chiave pubblica dal Certificato X509
            PublicKey publicKey = ValidateUtils.extractPublicKeyFromHolder(certHolder);
            // - Estrazione delle firme di ogni firmatario dal SignedData
            List<byte[]> signatures = ValidateUtils.extractSignaturesFromSignedData(cms);

            /*****************************************************
             ** PASSO 2 - VERIFICA ed ESTRAZIONE DEGLI HASH: CONTENT
             *****************************************************/
            if (!ValidateUtils.verifyMatchHashContent(cms)) {
                System.err.println("The hashes do not match");
                return false;
            }

            /*******************************************************************
             ** PASSO 1A: ESTRAZIONE DEGLI ATTRIBUTI FIRMATI (signedAttributes)
             *******************************************************************/
            Hashtable<ASN1ObjectIdentifier, Attribute> signedAttributesTable = ValidateUtils.extractAllSignedAttributes(cms);

            /*******************************************************************
             ** PASSO 1B: ANALISI DEGLI HASH DEI DATI (DataGroupHashes)
             *********************************************************************/
            // Estrazione e verifica della lista degli hash dei Data Group
            if (!ValidateUtils.verifyNisSha256FromDataGroup(cms, cieIas.getNis())) {
                System.err.println("The NIS hashes DataGroup do not match with the expected value");
                return false;
            }
            /*******************************************************************
             ** PASSO 2: MANIPOLAZIONE DEL TAG PER LA VERIFICA DELLA FIRMA
             *******************************************************************/
            //Quando viene richiesto l'array di byte con getEncoded(), BouncyCastle formatta correttamente,
            //aggiungendo il tag 0x31 e la lunghezza, in modo che l'output sia pronto per l'algoritmo di hash SHA-256.

            /*******************************************************************
             **  PASSO 3: VERIFICA FINALE DELLA FIRMA DIGITALE
             ********************************************************************/
            /* NB: LE DUE SEGUENTI VERIFICHE SONO USATE NELLO SCRIPT SHELL E QUI SOLO COME TEST
            if (!ValidateUtils.veryfySignedAttrIsSet(cms)) {
                //System.err.println("SignedAttribute content do not match the expected value");
                return false;
            }
            if (!ValidateUtils.veryfySignatures(cms)) {
                //System.err.println("Signature do not match the expected value");
                return false;
            }
            */
            return ValidateUtils.verifyDigitalSignature(cms);

        } catch (CieCheckerException | CMSException | IOException | CertificateException | NoSuchAlgorithmException | OperatorCreationException e ){
            System.err.println("Error during verification SOD: " + e.getMessage());
            return false;
        }
    }


}
