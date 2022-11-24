package it.pagopa.pn.mandate.services.mandate.v1;

import java.util.Optional;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
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

    
    private final MandateDao mandateDao;
    private final MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper;
    
    public MandatePrivateService(MandateDao mandateDao, MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper) {
        this.mandateDao = mandateDao;
        this.mandateEntityInternalMandateDtoMapper = mandateEntityInternalMandateDtoMapper;
    }

    public Flux<InternalMandateDto> listMandatesByDelegate(String internaluserId, String mandateId) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        log.info("listing private mandates by delegate for internaluserId={} mandateId={}", internaluserId, mandateId);
        return mandateDao.listMandatesByDelegate(internaluserId, StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE), mandateId)
                .map(mandateEntityInternalMandateDtoMapper::toDto)
                .map(mand -> {
                    log.info("listMandatesByDelegate found mandate={}",mand);
                    return mand;
                });
    }

    public Flux<InternalMandateDto> listMandatesByDelegator(String internaluserId, String mandateId) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        log.info("listing private mandates by delegate for internaluserId={} mandateId={}", internaluserId, mandateId);
        return mandateDao.listMandatesByDelegator(internaluserId, StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE), mandateId)
                .map(mandateEntityInternalMandateDtoMapper::toDto)
                .map(mand -> {
                    log.info("listMandatesByDelegator found mandate={}",mand);
                    return mand;
                });
    }
}
