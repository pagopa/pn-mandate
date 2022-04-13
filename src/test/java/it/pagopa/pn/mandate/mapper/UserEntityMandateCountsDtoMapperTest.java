package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.mandate.middleware.db.entities.DelegateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
class UserEntityMandateCountsDtoMapperTest {

    @Autowired
    UserEntityMandateCountsDtoMapper mapper;

    @Test
    void toEntity() {
        try {
            mapper.toEntity(null);
            fail("no UnsupportedOperationException throw, write test");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    void toDto() {
        //Given
        DelegateEntity delegateToInsert = new DelegateEntity();
        delegateToInsert.setPendingcount(5);
        delegateToInsert.setPk("123");
        delegateToInsert.setSk("abc");


        //When
        MandateCountsDto result = mapper.toDto(delegateToInsert);

        //Then
        try {
            Assertions.assertNotNull( result);
            Assertions.assertNotNull(result.getValue());

            Assertions.assertEquals(delegateToInsert.getPendingcount(),  result.getValue());
        } catch (Exception e) {
            fail(e);
        }
    }
}