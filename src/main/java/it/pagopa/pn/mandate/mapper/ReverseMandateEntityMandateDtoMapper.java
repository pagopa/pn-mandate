package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoRequest;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoResponse;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.UserDto;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class ReverseMandateEntityMandateDtoMapper {


    public MandateEntity toEntity(MandateDtoRequest dto) {
        MandateEntity target = new MandateEntity();
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

    public MandateDtoResponse toDto(MandateEntity entity, UserDto delegator, UserDto delegate) {
        final MandateDtoResponse target = new MandateDtoResponse();

        target.setMandateId(entity.getMandateId());
        target.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        target.setDateto(DateUtils.formatDate(entity.getValidto()));
        target.setStatus(ReverseMandateStatusEnumMapper.fromValue(entity.getState()));
        target.setDelegate(delegate);
        target.setDelegator(delegator);

        return target;
    }

}
