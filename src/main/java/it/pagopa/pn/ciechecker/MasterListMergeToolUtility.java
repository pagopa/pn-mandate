package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.client.s3.S3BucketClient;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.utils.LogsCostant;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.OK;
import static it.pagopa.pn.ciechecker.CieCheckerConstants.PROTOCOLLO_S3;

@lombok.CustomLog
public class MasterListMergeToolUtility {

    private static String cscaPath;
    //private static final String cscaPath = "s3://pn-runtime-environment-variables-eu-south-1-830192246553/pn-mandate/csca-masterlist/IT_MasterListCSCA.zip";
    //private static final String certPemPath = "s3://pn-runtime-environment-variables-eu-south-1-830192246553/pn-mandate/csca-masterlist/catest.pem";
    private static S3BucketClient s3BucketClient;
    private static InputStream inputStreamCscaAnchor;
    private static InputStream inputStreamCscaPem;

    private static String[] s3UriInfoCscaZip ;
    private static String[] s3UriInfoCertPem ;

    private static final String resourcesDir = "src/test/resources/";
    private static final String newFileZip = "new_IT_MasterListCSCA.zip";

    public MasterListMergeToolUtility(S3BucketClient s3BucketClient, String cscaPath) throws CieCheckerException {
        this.s3BucketClient = s3BucketClient;
        if(Objects.isNull(cscaPath) || cscaPath.isBlank())
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NO_CSCA_ANCHORS_PROVIDED);
        if(!cscaPath.endsWith("/"))
            cscaPath += "/";
        this.cscaPath = cscaPath;
    }


    public ResultCieChecker merge() throws CieCheckerException{

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.MASTERLISTMERGETOOL_MERGE);
        s3UriInfoCscaZip = ValidateUtils.extractS3Components( this.cscaPath + "IT_MasterListCSCA.zip");
        s3UriInfoCertPem = ValidateUtils.extractS3Components( this.cscaPath + "catest.pem");

        if (this.cscaPath.startsWith(PROTOCOLLO_S3) ){

            inputStreamCscaAnchor = s3BucketClient.getObjectContent(this.cscaPath+ "IT_MasterListCSCA.zip");
            inputStreamCscaPem = s3BucketClient.getObjectContent(this.cscaPath + "catest.pem");
            if (Objects.isNull(inputStreamCscaAnchor) || Objects.isNull(inputStreamCscaPem) ) {
                log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_MERGE, ResultCieChecker.KO_EXC_NOFOUND_FILEARGS.getValue());
                return ResultCieChecker.KO_EXC_NOFOUND_FILEARGS;
            }
            log.debug("inputStreamCscaAnchor: {}", inputStreamCscaAnchor);
            log.debug("inputStreamCscaPem: {}", inputStreamCscaPem);
        } else {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_MERGE,ResultCieChecker.KO_EXC_NOVALID_URI_CSCA_ANCHORS.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_NOVALID_URI_CSCA_ANCHORS);
        }

        try {
            ResultCieChecker result = addFileToMasterListZip();
            // Output atteso:
            // tre file: il file "catest.pem" da aggiungere al nuovo zip, "new_<fileNameZip>.zip" (il nuovo ZIP) e "fileNameZip.zip" (il originale ZIP)
            if(result.getValue().equals(OK)) {
                log.debug("UPLOAD del file prodotto sul bucket S3...");
                //UPLOAD del file prodotto sul bucket S3
                writeNewMasterZip(  new FileInputStream(resourcesDir + newFileZip));
            }
            return ResultCieChecker.OK;
        } catch (Exception e) {
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_MERGE, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO, e);
        }
    }

    private ResultCieChecker addFileToMasterListZip() throws CieCheckerException, IOException {

        log.info(LogsCostant.INVOKING_OPERATION_LABEL, LogsCostant.MASTERLISTMERGETOOL_ADDFILETOMASTERZIP);
        log.debug("s3UriInfoCscaZip[2]: {}" , s3UriInfoCscaZip[2]);
        log.debug("s3UriInfoCertPem[2]: {}" , s3UriInfoCertPem[2]);

        Path targetPath = Path.of(resourcesDir);
        Path parentDir = targetPath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        //creo un file zip temporaneo
        File tempFile = new File(resourcesDir + "new_" + s3UriInfoCscaZip[2]);
        log.debug("tempFilePath CscaZip: {}", tempFile.getAbsolutePath());

        boolean success = false;
        String s3UriInfoCertPem_2 = s3UriInfoCertPem[2];
        log.debug("s3UriInfoCertPem_2: {}", s3UriInfoCertPem_2);
        try (ZipInputStream zis = new ZipInputStream(inputStreamCscaAnchor );
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))){
            ZipEntry entry;
            //Copia il contenuto del file ZIP originale in quello temporaneo
            //log.debug("ZIS: {}", zis.available());
            while ((entry = zis.getNextEntry()) != null) {
//                log.info("entry.getName(): {}", entry.getName());
//                log.info("s3UriInfoCertPem_2: {}", s3UriInfoCertPem_2);
                if (!entry.getName().equals(s3UriInfoCertPem_2)) {
                    log.debug("Copia il contenuto del file ZIP originale in quello temporaneo: {}", entry.getName());
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zis.transferTo(zos);
                    zos.closeEntry();
                }
            }

            //Aggiungo il nuovo file allo ZIP temporaneo
            log.debug("Aggiungo il nuovo file allo ZIP temporaneo - s3UriInfoCertPem_2: {} " , s3UriInfoCertPem_2);
            try (FileInputStream fis = convertToFileInputStream(inputStreamCscaPem, s3UriInfoCertPem_2)) {
                ZipEntry newEntry = new ZipEntry(s3UriInfoCertPem_2);
                zos.putNextEntry(newEntry);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    zos.write(buffer, 0, bytesRead ); // attenzione: l’ordine dei parametri deve essere corretto
                }
                zos.closeEntry();
                zos.flush();
            }
            //log.debug("fileToAdd.getName(): {}", s3UriInfoCertPem_2);
            success = true; //file temporaneo creato
        }
        // Assicurati che il file temporaneo venga gestito correttamente
        if (success) {
            log.info(LogsCostant.SUCCESSFUL_OPERATION_NO_RESULT_LABEL, "File '" + s3UriInfoCertPem[1] + "' aggiunto con successo a '" + s3UriInfoCscaZip[3] +"new_"+s3UriInfoCscaZip[2]+ "'");
            return ResultCieChecker.OK;
        } else {
            // Delete del file temporaneo in caso di eccezione
            Files.deleteIfExists(parentDir);
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_ADDFILETOMASTERZIP, ResultCieChecker.KO_EXC_CREATION_FILEZIPTEMP.getValue());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_CREATION_FILEZIPTEMP);
        }
    }

    private static FileInputStream convertToFileInputStream(InputStream genericStream, String fileName) throws IOException {

        //Crea un file temporaneo
        File tempFile = Files.createTempFile(fileName, ".tmp").toFile();
        // Scrive il contenuto del flusso generico nel file temporaneo
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            genericStream.transferTo(fos); // Metodo efficiente per copiare tutti i byte
        }
        // Crea il nuovo FileInputStream dal file temporaneo salvato
        FileInputStream fileInputStream = new FileInputStream(tempFile);

        // ATTENZIONE: Il file temporaneo esiste ancora sul disco.
        // L'applicazione è responsabile della sua eliminazione dopo l'uso!
        if(tempFile.exists())
            tempFile.deleteOnExit(); // Utile per la pulizia automatica all'uscita dalla JVM
        genericStream.close();

        return fileInputStream;
    }


    public ResultCieChecker writeNewMasterZip( InputStream newFileZipZos) throws CieCheckerException {

        try {
            byte[] newZipFileBytes = newFileZipZos.readAllBytes();
            InputStream newZip = new ByteArrayInputStream(newZipFileBytes);
            s3BucketClient.uploadContent(cscaPath, newZip, newZipFileBytes.length, null);
            log.info(LogsCostant.SUCCESSFUL_OPERATION_NO_RESULT_LABEL, "New File Archive uploaded on S3Bucket");
            return ResultCieChecker.OK;
        }catch (Exception e ){
            log.error(LogsCostant.EXCEPTION_IN_PROCESS, LogsCostant.MASTERLISTMERGETOOL_ADDFILETOMASTERZIP, e.getMessage());
            throw new CieCheckerException(ResultCieChecker.KO_EXC_UPLOAD_NEWFILEZIP_TO_S3, e);
        }
    }
}
