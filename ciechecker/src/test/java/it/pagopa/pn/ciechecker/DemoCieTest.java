package it.pagopa.pn.ciechecker;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@ActiveProfiles("test")
@SpringBootTest
@Tag("TestDemo")
public class DemoCieTest {

    @Test
    void testDemo(){
        System.out.println("TEST DEMO");
    }
}
