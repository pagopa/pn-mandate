package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoRequest;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ReverseMandateEntityMandateDtoMapper {

    private final SecureRandom random = new SecureRandom();

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
        int randomNumber = random.nextInt(100000);
        return String.format("%05d", randomNumber);
    }

}
