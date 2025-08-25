package it.pagopa.pn.ciechecker.utils;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ValidateUtils {

    private ValidateUtils() {}

    /// -> TO TEST
    public static byte[] hexFile(String toHex) throws DecoderException {
        return Hex.decodeHex(toHex);
    }

    public static String cleanString(Path file) throws IOException {
        return Files.readString(file).replaceAll("\\s+", "");
    }

}
