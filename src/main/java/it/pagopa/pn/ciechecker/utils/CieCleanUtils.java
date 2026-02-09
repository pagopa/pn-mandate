package it.pagopa.pn.ciechecker.utils;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Base64;

@Slf4j
public class CieCleanUtils {
    public static final int NIS_LENGTH = 12; // lunghezza fissa in byte

    public static void cleanIas(CieIas ias) {
        log.info("Invoking operation cleanIas()");
        if (ias == null) {
            log.info("cleanIas() - CieIas è null, skip della pulizia per IAS");
            return;
        }
        cleanPublicKey(ias);
        cleanSodField(ias.getSod(), "SOD IAS");
        cleanNisField(ias);
    }

    public static void cleanMrtd(CieMrtd mrtd) {
        log.info("Invoking operation cleanMrtd()");
        if (mrtd == null) {
            log.info("cleanMrtd() - CieMrtd è null, skip della pulizia per MRTD");
            return;
        }
        cleanSodField(mrtd.getSod(), "SOD MRTD");
        mrtd.setDg1(cleanAsn1OrTlvField(mrtd.getDg1(), "DG1"));
        mrtd.setDg11(cleanAsn1OrTlvField(mrtd.getDg11(), "DG11"));
    }


    private static void cleanPublicKey(CieIas ias) {
        log.info("Invoking operation cleanPublicKey()");
        if (ias.getPublicKey() == null) return;

        byte[] before = ias.getPublicKey();
        log.info("cleanPublicKey() - IAS PublicKey prima della pulizia (length): {}", before.length);

        byte[] cleaned = cleanAsn1OrTlvField(before, "PublicKey");
        ias.setPublicKey(cleaned);

        log.info("cleanPublicKey() - IAS PublicKey dopo la pulizia (length): {}", cleaned!=null ? cleaned.length : "null");
    }

    private static void cleanNisField(CieIas ias) {
        log.info("Invoking operation cleanNisField()");
        if (ias.getNis() == null) return;
        byte[] before = ias.getNis();
        log.info("cleanNisField() - NIS prima della pulizia: ({} byte)", before.length); // HexFormat.of().formatHex(before)
        byte[] after = cleanNis(before);
        ias.setNis(after);
        log.info("cleanNisField() - NIS dopo la pulizia:({} byte)", after != null ? after.length : "null");
        if (Arrays.equals(before, after)) {
            log.info("cleanNisField() - NIS già corretto, nessuna correzione sul padding");
        }
    }

    private static void cleanSodField(byte[] sod, String label) {
        log.info("Invoking operation cleanSodField()");
        if (sod == null) {
            log.warn("cleanSodField() - SOD è null, skip della pulizia per {}", label);
            return;
        }
        log.info("cleanSodField() - {} prima della pulizia (length={}):", label, sod.length);
        byte[] cleaned = cleanAsn1OrTlvField(sod, label);
        log.info("cleanSodField() - {} dopo la pulizia (length={})", label, cleaned!=null ? cleaned.length : "null");
        if (Arrays.equals(sod, cleaned)) {
            log.info("cleanSodField() - {} già corretto: nessuna pulizia del padding", label);
        }
    }
    /**
     * Pulisce un campo ASN.1 o TLV rimuovendo eventuali padding finali o byte extra.
     * Supporta chiavi pubbliche (SEQUENCE 0x30) e DG.
     * In ASN.1 DER/TLV, il byte della lunghezza può essere codificato in due modi: short form o long form: va individuato
     * per sapere la lunghezza effettiva
     *
     * @param fieldBytes byte array da pulire
     * @return byte array pulito, troncato alla lunghezza ASN.1 reale
     * @throws CieCheckerException se il TLV è invalido o la lunghezza non corrisponde
     */
    public static byte[] cleanAsn1OrTlvField(byte[] fieldBytes, String label) {
        if (fieldBytes == null) {
            log.warn("cleanAsn1OrTlvField() - {} nullo, skip della pulizia", label);
            return null;
        }

        log.info("cleanAsn1OrTlvField() - {} prima della pulizia ({} byte)", label, fieldBytes.length);
        //un TLV valido deve avere almeno 2 byte: 1 per il tag e 1 per la lunghezza
        if (fieldBytes.length < 2) {
            log.warn("cleanAsn1OrTlvField() - {}: lunghezza troppo corta per TLV valido", label);
            return fieldBytes;
        }
        //recupero del byte di lunghezza
        int lengthByte = fieldBytes[1] & 0xFF;
        int realLength;
        // caso TLV (<= 127), il byte indica direttamente la lunghezza del valore
        if (lengthByte < 0x80) {
            realLength = 2 + lengthByte; // 1 byte tag + 1 byte lunghezza + lunghezza valore
        } else { // il primo bit indica che la lunghezza è codificata su più byte
            int numLengthBytes = lengthByte & 0x7F; // numero di byte utilizzati per la lunghezza
            if (numLengthBytes + 2 > fieldBytes.length) {
                log.error("cleanAsn1OrTlvField() - {}: lunghezza dei byte > dimensione del campo", label);
                throw new CieCheckerException(ResultCieChecker.KO_EXC_CLEANING);
            }
            //calcolo della lunghezza effettiva del valore combinando i byte della lunghezza
            int valueLength = 0;
            for (int i = 0; i < numLengthBytes; i++) {
                valueLength = (valueLength << 8) | (fieldBytes[2 + i] & 0xFF);
            }
            realLength = 2 + numLengthBytes + valueLength;
        }
        //controlla che la lunghezza reale non ecceda la dimensione del campo
        if (realLength > fieldBytes.length) {
            log.error("cleanAsn1OrTlvField() - {}: lunghezza ASN.1 reale > lunghezza del campo", label);
            throw new CieCheckerException(ResultCieChecker.KO_EXC_CLEANING);
        }
        //troncamento di eventuali byte extra
        byte[] cleaned = Arrays.copyOf(fieldBytes, realLength);

        log.info("cleanAsn1OrTlvField() - {} dopo la pulizia ({} byte)", label, cleaned.length);

        if (!Arrays.equals(fieldBytes, cleaned)) {
            log.warn("cleanAsn1OrTlvField() - {}: padding rimosso: {} byte", label, fieldBytes.length - cleaned.length);
        } else {
            log.info("cleanAsn1OrTlvField() - {} già corretto, nessuna pulizia di padding necessaria", label);
        }

        return cleaned;
    }

    public static byte[] cleanNis(byte[] nisBytes) {
        log.info("Invoking operation cleanNis()");
        if (nisBytes == null) {
            log.warn("cleanNis() - NIS null, skip della pulizia");
            return null;
        }

        if (nisBytes.length == NIS_LENGTH) {
            log.info("cleanNis() - NIS già corretto, lunghezza: {} byte", nisBytes.length);
            return nisBytes;
        }

        if (nisBytes.length > NIS_LENGTH) {
            log.warn("cleanNis() - NIS troppo lungo, tagliamo a {} byte", NIS_LENGTH);
            return Arrays.copyOf(nisBytes, NIS_LENGTH);
        }

        log.error("cleanNis() - NIS troppo corto, lunghezza: {} byte", nisBytes.length);
        throw new CieCheckerException(ResultCieChecker.KO_EXC_CLEANING);
    }

}
