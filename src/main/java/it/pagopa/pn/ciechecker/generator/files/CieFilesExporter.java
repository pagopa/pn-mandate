package it.pagopa.pn.ciechecker.generator.files;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.generator.model.CieCaAndKey;
import it.pagopa.pn.ciechecker.model.*;
import it.pagopa.pn.ciechecker.utils.LogsConstant;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static it.pagopa.pn.ciechecker.generator.constants.CieGeneratorConstants.CTX_DOCUMENT_SIGNER_INFO;
import static it.pagopa.pn.ciechecker.generator.constants.CieGeneratorConstants.CTX_VALIDATION_DATA;


@lombok.CustomLog
public class CieFilesExporter {

    // Mappa per memorizzare tutti gli oggetti sorgente: { "nomeCampo", istanzaOggetto }
    private final Map<String, Object> sourceObjects;
    private final String outputBaseDir; // Directory base per l'output

    public CieFilesExporter(CieValidationData validationData, CieCaAndKey cieCaAndKey, String baseDir) throws CieCheckerException{

        if(!validateDataInput(validationData, cieCaAndKey, baseDir))
            throw new CieCheckerException(ResultCieChecker.KO_EXC_INPUT_PARAMETER_NULL);

        this.sourceObjects = new HashMap<>();
        this.sourceObjects.put(CTX_VALIDATION_DATA, validationData);
        this.sourceObjects.put(CTX_DOCUMENT_SIGNER_INFO, cieCaAndKey);

        this.outputBaseDir = baseDir;
    }

    public boolean validateDataInput(CieValidationData data, CieCaAndKey cieCaAndkey, String baseDir) throws CieCheckerException {

        log.info(LogsConstant.INVOKING_OPERATION_LABEL, LogsConstant.CIEFILEGENERATOR_VALIDATE_DATA_INPUT);
        if ( Objects.isNull(data) || Objects.isNull(data.getCieIas()) || Objects.isNull(data.getCieMrtd())) throw new CieCheckerException(ResultCieChecker.KO_EXC_INPUT_PARAMETER_NULL);
        if ( Objects.isNull(data.getCieIas().getSod()) || data.getCieIas().getSod().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CIESOD);
        if ( Objects.isNull(data.getCieIas().getNis()) || data.getCieIas().getNis().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CIENIS);
        if ( Objects.isNull(data.getCieIas().getPublicKey()) || data.getCieIas().getPublicKey().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_PUBLICKEY);
        if ( Objects.isNull(data.getSignedNonce()) || data.getSignedNonce().length == 0 ) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_SIGNEDNONCE);
        if ( Objects.isNull(data.getNonce()) || data.getNonce().isEmpty() ) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_NONCE);
        if ( Objects.isNull(data.getCieMrtd().getSod()) || data.getCieMrtd().getSod().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_MRTDSOD);
        if ( Objects.isNull(data.getCieMrtd().getDg1()) || data.getCieMrtd().getDg1().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_MRTDDG1);
        if ( Objects.isNull(data.getCieMrtd().getDg11()) || data.getCieMrtd().getDg11().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_MRTDDG11);
        if ( Objects.isNull(data.getCodFiscDelegante()) || data.getCodFiscDelegante().isBlank()) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CODFISCDELEGANTE);

        if ( Objects.isNull(cieCaAndkey.getCertPem()) || cieCaAndkey.getCertPem().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CERTPEM);
        if ( Objects.isNull(cieCaAndkey.getCertKey()) || cieCaAndkey.getCertKey().length == 0) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CERTKEY);

        if(Objects.isNull(baseDir) || baseDir.isBlank()) throw new CieCheckerException(ResultCieChecker.KO_EXC_INVALID_PARAMETER_BASEDIR);
        log.info(LogsConstant.SUCCESSFUL_OPERATION_NO_RESULT_LABEL, LogsConstant.CIEFILEGENERATOR_VALIDATE_DATA_INPUT);
        return true;
    }

    /**
     * Itera su tutti gli attributi definiti nell'Enum e genera un file con il percorso definito.
     * @return Una mappa contenente il percorso del file generato e la dimensione in byte.
     * @throws CieCheckerException Se la Reflection fallisce o si verifica un errore I/O.
     */
    public Map<String, Long> exportCieArtifactsToFiles() throws CieCheckerException {

        log.info(LogsConstant.INVOKING_OPERATION_LABEL, LogsConstant.CIEFILEGENERATOR_EXPORTFILES);
        Map<String, Long> exportResults = new HashMap<>();
        try {
            for (CieFileAttribute attr : CieFileAttribute.values()) {

                //System.out.println("attr.getRelativeFilePath(): " + attr.getRelativeFilePath());
                // Determina il percorso completo
                String fullPath = outputBaseDir + File.separator + attr.getRelativeFilePath();
                if (attr.getAttributePath().indexOf("cieCaAndKey") == 0 && Files.exists(Path.of(fullPath))) {
                    //  System.out.println("SALTO");
                    continue;
                }
                //Cancello il file esistente prima di rigenerarlo
                deleteIfExists(fullPath);

                //System.out.println("NON SALTO ");
                // Determina il byte[] tramite reflection
                byte[] data = getBytesByPath(attr.getAttributePath());

                if (data != null && data.length > 0) {
                    // Esporta il file
                    File exportedFile = exportBytesToFile(data, fullPath);
                    exportResults.put(fullPath, exportedFile.length());
                    log.debug("File esportato: {} ({} bytes)", exportedFile.getName(), exportedFile.length());
                } else {
                    log.error("Attributo {} è vuoto o nullo", attr.name());
                    throw new CieCheckerException(ResultCieChecker.KO_EXC_ATTRIBUTO_NULL);
                }
            }
            return exportResults;
        }catch (Exception e ){
            log.logEndingProcess(LogsConstant.CIEFILEGENERATOR_EXPORTFILES, false, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    private boolean deleteIfExists(String fileNamePath) throws CieCheckerException {
        try {
            Path fileToDelete = Paths.get(fileNamePath);
            return Files.deleteIfExists(fileToDelete);
        } catch (IOException e) {
            log.error(LogsConstant.EXCEPTION_IN_PROCESS, LogsConstant.CIEFILEGENERATOR_EXPORTFILES, ResultCieChecker.KO_EXC_DELETEEXISTFILE.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_DELETEEXISTFILE, e);
        }
    }

    /**
     * Utilizzo la Reflection per recuperare un byte[] basato su un percorso attributo (es. "cieIas.sod").
     */
    private byte[] getBytesByPath(String path) throws Exception {
        String[] parts = path.split("\\.", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Formato percorso attributo non valido. Serve almeno [oggetto].[campo] o [oggetto].[sotto-oggetto].[campo]: " + path);
        }

        String objectName = parts[0];
        Object currentObject = sourceObjects.get(objectName);
        if (currentObject == null) {
            throw new IllegalArgumentException("Oggetto non trovato: " + objectName);
        }
        Field targetField;
        String fieldPath = parts[1]; // Il percorso rimanente (es. "certPem" o "cieIas.sod")
        if (parts.length == 2) {
            // CASO 1: Campo di Primo Livello (es. cieCaAndKey.certPem o validationData.signedNonce)
            // fieldPath = "certPem" o "signedNonce"
            targetField = currentObject.getClass().getDeclaredField(fieldPath);
        } else { // parts.length == 3
            // CASO 2: Campo Annidato (es. validationData.cieIas.sod)
            // parts[1] è l'oggetto intermedio (es. "cieIas"), parts[2] è il campo finale (es. "sod")

            // Leggo il campo intermedio (es. cieIas)
            Field intermediateField = currentObject.getClass().getDeclaredField(parts[1]);
            intermediateField.setAccessible(true);
            currentObject = intermediateField.get(currentObject);
            if (currentObject == null) {
                throw new NullPointerException("Oggetto nullo: " + objectName);
            }

            // Leggo il campo finale (es. sod)
            targetField = currentObject.getClass().getDeclaredField(parts[2]);
        }
        if (targetField == null) {
            throw new NoSuchFieldException("Impossibile trovare il campo finale per il percorso: " + path);
        }
        targetField.setAccessible(true);
        // Verifico che sia un byte[] e recupero il valore
        if (targetField.getType().isArray() && targetField.getType().getComponentType() == byte.class) {
            return (byte[]) targetField.get(currentObject);
        } else {
            throw new IllegalStateException("Attributo non è di tipo byte[]: " + path);
        }

    }

    private static File exportBytesToFile(byte[] binaryData, String destinationPath) throws IOException {
        Path targetPath = Path.of(destinationPath);
        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        String base64String = Base64.getEncoder().encodeToString(binaryData);
        Files.writeString(
              targetPath,
              base64String,
              StandardCharsets.UTF_8
        );
        return targetPath.toFile();
    }

}
