package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.mandate.middleware.db.MandateDaoTestIT;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MandateEntityMandateDtoMapperTest {

    @Autowired
    MandateEntityMandateDtoMapper mapper;

    @Test
    void toEntity() {
        //Given
        MandateEntity mandateToInsert = MandateDaoTestIT.newMandate(true);
        MandateDto dto = mapper.toDto(mandateToInsert);

        //When
        MandateEntity result = mapper.toEntity(dto);

        //Then
        try {
            Assertions.assertNotNull( result);
            Assertions.assertNotNull(result.getMandateId());
            Assertions.assertNotNull(result.getValidfrom());
            Assertions.assertNotNull(result.getValidto());
            Assertions.assertNotNull(result.getValidationcode());

            Assertions.assertEquals(mandateToInsert.getMandateId(),  result.getMandateId());
            Assertions.assertEquals(mandateToInsert.getValidfrom(),  result.getValidfrom());
            Assertions.assertEquals(mandateToInsert.getValidto(),  result.getValidto());
            Assertions.assertEquals(mandateToInsert.getValidationcode(),  result.getValidationcode());
            // implicano la risoluzione da datavault, non ha senso testarli
            //Assertions.assertEquals(mandateToInsert.getDelegateisperson(),  result.getDelegate().getPerson());
            //Assertions.assertEquals(mandateToInsert.getDelegatorisperson(),  result.getDelegator().getPerson());
            Assertions.assertEquals(mandateToInsert.getVisibilityIds()==null?0:mandateToInsert.getVisibilityIds().size(),
                        result.getVisibilityIds()==null?0:result.getVisibilityIds().size());

        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void toDto() {
        //Given
        MandateEntity mandateToInsert = MandateDaoTestIT.newMandate(true);

        //When
        MandateDto result = mapper.toDto(mandateToInsert);

        //Then
        try {
            Assertions.assertNotNull( result);
            Assertions.assertNotNull(result.getMandateId());
            Assertions.assertNotNull(result.getDatefrom());
            Assertions.assertNotNull(result.getDateto());
            Assertions.assertNotNull(result.getDelegator());
            Assertions.assertNotNull(result.getDelegate());
            Assertions.assertNotNull(result.getStatus());
            Assertions.assertNotNull(result.getVerificationCode());


            Assertions.assertEquals(mandateToInsert.getMandateId(),  result.getMandateId());
            Assertions.assertEquals(mandateToInsert.getValidfrom(),  result.getDatefrom());
            Assertions.assertEquals(mandateToInsert.getValidto(),  result.getDateto());
            Assertions.assertEquals(mandateToInsert.getState(), StatusEnumMapper.intValfromStatus(result.getStatus()));
            Assertions.assertEquals(mandateToInsert.getValidationcode(),  result.getVerificationCode());
            Assertions.assertEquals(mandateToInsert.getDelegateisperson(),  result.getDelegate().getPerson());
            Assertions.assertEquals(mandateToInsert.getDelegatorisperson(),  result.getDelegator().getPerson());
            Assertions.assertEquals(mandateToInsert.getVisibilityIds()==null?0:mandateToInsert.getVisibilityIds().size(),  result.getVisibilityIds().size());

        } catch (Exception e) {
            fail(e);
        }
    }
}