package it.pagopa.pn.mandate.utils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
@Component
public class MandateUtils {
    private final SecureRandom random= new SecureRandom();

    public MandateUtils(){}

    public String generateRandomCode() {
        int randomNumber = random.nextInt(100000);
        return String.format("%05d", randomNumber);
    }
}
