package it.pagopa.pn.mandate.utils;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
@Component
@AllArgsConstructor
public class MandateUtils {
    private final SecureRandom random= new SecureRandom();

    public String generateRandomCode() {
        int randomNumber = random.nextInt(100000);
        return String.format("%05d", randomNumber);
    }
}
