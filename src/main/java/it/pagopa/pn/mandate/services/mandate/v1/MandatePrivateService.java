package it.pagopa.pn.mandate.services.mandate.v1;

import java.util.Optional;

import org.springframework.stereotype.Service;

import it.pagopa.pn.mandate.mapper.MandateEntityInternalMandateDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono; 

@Service
@Slf4j
public class MandatePrivateService {

    
    private MandateDao mandateDao;
    private MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper;
    
    public MandatePrivateService(MandateDao mandateDao, MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper) {
        this.mandateDao = mandateDao;
        this.mandateEntityInternalMandateDtoMapper = mandateEntityInternalMandateDtoMapper;
    }

    public Flux<InternalMandateDto> listMandatesByDelegate(String internaluserId) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        if (log.isInfoEnabled())
                    log.info("listing mandates by delegate for " + internaluserId);
        return mandateDao.listMandatesByDelegate(internaluserId, Optional.of(StatusEnum.ACTIVE.getValue()))
                .flatMap(ent -> mandateEntityInternalMandateDtoMapper.toDto(Mono.just(ent)));
    }

    public Flux<InternalMandateDto> listMandatesByDelegator(String internaluserId) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        if (log.isInfoEnabled())
                    log.info("listing mandates by delegator for " + internaluserId);
        return mandateDao.listMandatesByDelegator(internaluserId, Optional.of(StatusEnum.ACTIVE.getValue()))
                .flatMap(ent -> mandateEntityInternalMandateDtoMapper.toDto(Mono.just(ent)));
    }
    
}
