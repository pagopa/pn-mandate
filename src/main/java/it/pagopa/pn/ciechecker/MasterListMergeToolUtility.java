package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.utils.LogsCostant;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@lombok.CustomLog
public class MasterListMergeToolUtility {

    private static String originalFileNameZip;
    private static String fileNameToAdd;

    public MasterListMergeToolUtility(){}

    public static void main(String[] args){
        if (args.length != 2) {
            log.error("Errore: Devi fornire esattamente due argomenti (path del file ZIP e path del file da aggiungere)");
            return;
        }
        originalFileNameZip = args[0];
        fileNameToAdd = args[1];
        try {
            MasterListMergeToolUtility master = new MasterListMergeToolUtility();
            master.merge(originalFileNameZip, fileNameToAdd);
        }catch(CieCheckerException e){
            e.printStackTrace();
            log.error("MasterListMergeToolUtility.main() ", e.getMessage());
        }
    }

    public ResultCieChecker merge(String originalFileNameZip, String fileNameToAdd) throws CieCheckerException{

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.MASTERLISTMERGETOOL_MERGE);
        if (Objects.isNull(originalFileNameZip) || Objects.isNull(fileNameToAdd) || originalFileNameZip.isBlank() || fileNameToAdd.isBlank()) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_MERGE, ResultCieChecker.KO_EXC_NOFOUND_FILEARGS.getValue());
            return ResultCieChecker.KO_EXC_NOFOUND_FILEARGS;
        }

        try {
            log.debug("Input Arguments: {} - {}", originalFileNameZip, fileNameToAdd);
            File originalZip = new File(originalFileNameZip);
            File fileToAdd = new File(fileNameToAdd);

            if (!originalZip.exists() || !fileToAdd.exists()) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_MERGE, ResultCieChecker.KO_EXC_NOFOUND_FILEARGS.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_NOFOUND_FILEARGS);
            }
            addFileToMasterListZip(originalZip, fileToAdd);
            // Output atteso:
            // tre file: il file aggiunto, "orig_<fileNameZip>.zip" (il vecchio ZIP) e "fileNameZip.zip" (il nuovo ZIP)

        } catch (Exception e) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_MERGE, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
        return ResultCieChecker.OK;
    }


    private ResultCieChecker addFileToMasterListZip(File zipFile, File fileToAdd) throws CieCheckerException, IOException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.MASTERLISTMERGETOOL_ADDFILETOMASTERZIP);
        // Creo un file ZIP temporaneo
        String outputPathtFile = zipFile.getParent();
        String originalFileName = zipFile.getName();
        log.debug("pathFile: {} - Name: {}" , outputPathtFile , originalFileName);
        Path tempFilePath = Files.createTempFile(Path.of(outputPathtFile), zipFile.getName(), ".tmp");

        File tempFile = tempFilePath.toFile();

        boolean success = false;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {

            ZipEntry entry;
            //Copia il contenuto del file ZIP originale in quello temporaneo
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().equals(fileToAdd.getName())) {
                    log.debug("Copia il contenuto del file ZIP originale in quello temporaneo: {}", entry.getName());
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zis.transferTo(zos);
                }
                zos.closeEntry();
            }

            //Aggiungo il nuovo file allo ZIP temporaneo
            try (FileInputStream fis = new FileInputStream(fileToAdd)) {
                ZipEntry newEntry = new ZipEntry(fileToAdd.getName());
                zos.putNextEntry(newEntry);
                fis.transferTo(zos);
                zos.closeEntry();
            }
            log.debug("fileToAdd.getName(): {}" ,fileToAdd.getName());
            success = true; //file temporaneo creato

        } finally {
            // Assicurati che il file temporaneo venga gestito correttamente
            if (success) {
                // Sostituisci il file ZIP originale con quello temporaneo modificato
                //Files.move(tempFilePath, zipFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                //log.info("File {} aggiunto con successo a {}", fileToAdd.getName(), zipFile.getName());
                Path origNewName = zipFile.toPath().resolveSibling("orig_"+zipFile.getName());
                Files.move(zipFile.toPath(), origNewName, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // Rinomina il file ZIP TEMPORANEO (il nuovo archivio)
                log.debug("tempFilePath: " + tempFilePath.getFileName());
                Path newZipName = tempFilePath.resolveSibling( originalFileName); //+ zipFile.getName());
                log.debug("newZipName: {}", newZipName);
                log.debug("tempFilePath: {}", tempFilePath.toString());
                Files.move(tempFilePath, newZipName, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                log.info(LogsCostant.SUCCESSFUL_OPERATION_NO_RESULT_LABEL, "File '"+fileToAdd.getName()+"' aggiunto con successo a '"+ zipFile.getName()+"'");
                return ResultCieChecker.OK;

            } else {
                // Delete del file temporaneo in caso di eccezione
                Files.deleteIfExists(tempFilePath);
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_ADDFILETOMASTERZIP, ResultCieChecker.KO_EXC_CREATION_FILEZIPTEMP.getValue());
                throw new CieCheckerException(ResultCieChecker.KO_EXC_CREATION_FILEZIPTEMP);
            }
        }
    }
}
