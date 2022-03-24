package it.pagopa.pn.mandate.services.mandate.v1;

import it.pagopa.pn.mandate.mapper.MandateEntityInternalMandateDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
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
        return mandateDao.listMandatesByDelegate(internaluserId)
                .flatMap(ent -> mandateEntityInternalMandateDtoMapper.toDto(Mono.just(ent)));
    }

    public Flux<InternalMandateDto> listMandatesByDelegator(String internaluserId) {
        return mandateDao.listMandatesByDelegator(internaluserId)
                .flatMap(ent -> mandateEntityInternalMandateDtoMapper.toDto(Mono.just(ent)));
    }
    
}
