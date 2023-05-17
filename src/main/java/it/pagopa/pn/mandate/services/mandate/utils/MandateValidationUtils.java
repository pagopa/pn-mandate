package it.pagopa.pn.mandate.services.mandate.utils;

import it.pagopa.pn.commons.exceptions.ExceptionHelper;
import it.pagopa.pn.commons.exceptions.dto.ProblemError;
import it.pagopa.pn.commons.utils.ValidateUtils;
import it.pagopa.pn.mandate.exceptions.*;
import it.pagopa.pn.mandate.model.InputSearchMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.*;

@Component
@lombok.CustomLog
public class MandateValidationUtils {

    private static final String DELEGATE_FISCAL_CODE = "delegate.fiscalCode";
    private static final String VERIFICATION_CODE = "verificationCode";
    private static final String DELEGATE_PERSON = "delegate.person";
    private static final String DELEGATE = "delegate";
    private static final String DATE_TO = "dateTo";

    private final ValidateUtils validateUtils;

    public MandateValidationUtils(ValidateUtils validateUtils) {
        this.validateUtils = validateUtils;
    }


    public Mono<Void> validateCountRequest(String status) {
        String process = "validating count request";
        log.logChecking(process);
        // per ora l'unico stato supportato è il pending, quindi il filtro non viene passato al count
        // Inoltre, ritorno un errore se status != pending
        if (status == null || !status.equals(MandateDto.StatusEnum.PENDING.getValue())) {
            log.logCheckingOutcome(process, false, "invalid status requested");
            return Mono.error(new PnUnsupportedFilterException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_ASSERTENUM, "status"));
        }

        log.logCheckingOutcome(process, true);
        return Mono.empty();
    }

    @NotNull
    public AcceptRequestDto validateAcceptMandateRequest(String mandateId, AcceptRequestDto m) {
        String process = "validating accept mandate";
        log.logChecking(process);
        if (m == null || m.getVerificationCode() == null)
        {
            log.logCheckingOutcome(process, false, "invalid verification code");
            throw new PnInvalidVerificationCodeException();
        }

        if (mandateId == null)
        {
            log.logCheckingOutcome(process, false, "mandate not found");
            throw new PnMandateNotFoundException();
        }

        log.logCheckingOutcome(process, true);
        return m;
    }


    public void validateCreationRequestHimself( CxTypeAuthFleet cxTypeAuthFleet, final String requesterInternaluserId, final String delegateInternaluserId) {
        String process = "validating create mandate himself";
        log.logChecking(process);
        if (!CxTypeAuthFleet.PG.equals(cxTypeAuthFleet) && delegateInternaluserId.equals(requesterInternaluserId)) {
            log.logCheckingOutcome(process, false, "cannot delegate himself");
            throw new PnMandateByHimselfException();
        }

        log.logCheckingOutcome(process, true);
    }

    public MandateDto validateCreationRequest(MandateDto mandateDto) {
        String process = "validating create mandate";
        log.logChecking(process);
        // valida delegato
        if (mandateDto.getDelegate() == null) {
            log.logCheckingOutcome(process, false, "missing delegate");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DELEGATE);
        }
        if ((mandateDto.getDelegate().getFiscalCode() == null)) {
            log.logCheckingOutcome(process, false, "missing delegate taxid");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DELEGATE_FISCAL_CODE);
        }

        if ((mandateDto.getDelegate().getPerson() == null)) {
            log.logCheckingOutcome(process, false, "missing delegate isperson");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DELEGATE_PERSON);
        }

        if ((mandateDto.getDelegate().getPerson() && (mandateDto.getDelegate().getFirstName() == null || mandateDto.getDelegate().getLastName() == null))
                || (!mandateDto.getDelegate().getPerson() && mandateDto.getDelegate().getCompanyName() == null)) {
            log.logCheckingOutcome(process, false, "invalid delegate");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER, DELEGATE);
        }
        // codice verifica (5 caratteri)
        if (mandateDto.getVerificationCode() == null) {
            log.logCheckingOutcome(process, false, "invalid verification code");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, VERIFICATION_CODE);
        }
        if (!mandateDto.getVerificationCode().matches("\\d\\d\\d\\d\\d")) {
            log.logCheckingOutcome(process, false, "invalid verification code format");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_PATTERN, VERIFICATION_CODE);
        }

        if (Boolean.TRUE.equals(mandateDto.getDelegate().getPerson())
                && !validateUtils.validate(mandateDto.getDelegate().getFiscalCode(), true)) {
            log.logCheckingOutcome(process, false, "invalid delegate taxid");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_PATTERN, DELEGATE_FISCAL_CODE);
        }
        // le PG possono avere p.iva o CF!
        if (Boolean.FALSE.equals(mandateDto.getDelegate().getPerson())
                && !validateUtils.validate(mandateDto.getDelegate().getFiscalCode(), false)) {
            log.logCheckingOutcome(process, false, "invalid delegate taxid");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_PATTERN, DELEGATE_FISCAL_CODE);
        }

        // la delega richiede la data di fine
        if (!StringUtils.hasText(mandateDto.getDateto())) {
            log.logCheckingOutcome(process, false, "missing expire date");
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DATE_TO);
        }


        log.logCheckingOutcome(process, true);
        return mandateDto;
    }


    public void validateSearchRequest(InputSearchMandateDto searchDto) {
        String process = "validating create mandate";
        log.logChecking(process);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<InputSearchMandateDto>> errors = validator.validate(searchDto);
            if (!errors.isEmpty()) {
                log.warn("validation search input errors: {}", errors);
                log.logCheckingOutcome(process, false);
                List<ProblemError> problems = new ExceptionHelper(Optional.empty())
                        .generateProblemErrorsFromConstraintViolation(errors);
                throw new PnInvalidInputException(searchDto.getDelegateId(), problems);
            }
        }

        log.logCheckingOutcome(process, true);
    }

}
