package it.pagopa.pn.ciechecker;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;


@ActiveProfiles("test")
@Tag("CieChecker")
public class DemoCieTest {

    @Test
    void testDemo(){
        System.out.println("TEST DEMO");
    }
}
