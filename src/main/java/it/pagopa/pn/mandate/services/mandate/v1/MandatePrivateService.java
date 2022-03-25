package it.pagopa.pn.mandate.services.mandate.v1;

import java.util.Optional;

import it.pagopa.pn.mandate.mapper.MandateEntityInternalMandateDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono; 

public class MandatePrivateService {

    
    private MandateDao mandateDao;
    private MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper;
    
    public MandatePrivateService(MandateDao mandateDao, MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper) {
        this.mandateDao = mandateDao;
        this.mandateEntityInternalMandateDtoMapper = mandateEntityInternalMandateDtoMapper;
    }

    public Flux<InternalMandateDto> listMandatesByDelegate(String internaluserId) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        return mandateDao.listMandatesByDelegate(internaluserId, Optional.of(StatusEnum.ACTIVE.getValue()))
                .flatMap(ent -> mandateEntityInternalMandateDtoMapper.toDto(Mono.just(ent)));
    }

    public Flux<InternalMandateDto> listMandatesByDelegator(String internaluserId) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        return mandateDao.listMandatesByDelegator(internaluserId, Optional.of(StatusEnum.ACTIVE.getValue()))
                .flatMap(ent -> mandateEntityInternalMandateDtoMapper.toDto(Mono.just(ent)));
    }
    
}
