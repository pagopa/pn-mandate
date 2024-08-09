package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoRequest;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoResponse;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.UserDto;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MandateEntityMandateDtoB2bMapper {


    public MandateEntity toEntity(MandateDtoRequest dto) {
        final MandateEntity target = new MandateEntity();
        if (dto.getDateto() != null)
            target.setValidto(DateUtils.atStartOfDay(DateUtils.parseDate(dto.getDateto()).toInstant()).plusDays(1).minusSeconds(1).toInstant());
        target.setValidationcode(generateRandomCode());
        if (dto.getDelegator() != null)
            target.setDelegatorisperson(dto.getDelegator().getPerson());
        return target;
    }

    private String generateRandomCode() {
        Random random = new Random();
        int randomNumber = random.nextInt(100000);
        return String.format("%05d", randomNumber);
    }

    public MandateDtoResponse toDto(MandateEntity entity) {
        final MandateDtoResponse target = new MandateDtoResponse();

        target.setMandateId(entity.getMandateId());
        target.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        target.setDateto(DateUtils.formatDate(entity.getValidto()));
        target.setStatus(StatusEnumMapperB2b.fromValue(entity.getState()));
        // popolo delegato e delegante, con l'unica informazioni che conosco (il fatto se è PF/PG)
        target.setDelegate(new UserDto());
        target.getDelegate().setPerson(entity.getDelegateisperson());
        target.setDelegator(new UserDto());
        target.getDelegator().setPerson(entity.getDelegatorisperson());

        return target;
    }

}
