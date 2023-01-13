package it.pagopa.pn.mandate.services.mandate.v1;

import java.util.List;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.DelegateType;
import it.pagopa.pn.mandate.utils.PgUtils;
import org.springframework.stereotype.Service;

import it.pagopa.pn.mandate.mapper.MandateEntityInternalMandateDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class MandatePrivateService {


    private final MandateDao mandateDao;
    private final MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper;

    public MandatePrivateService(MandateDao mandateDao, MandateEntityInternalMandateDtoMapper mandateEntityInternalMandateDtoMapper) {
        this.mandateDao = mandateDao;
        this.mandateEntityInternalMandateDtoMapper = mandateEntityInternalMandateDtoMapper;
    }

    public Flux<InternalMandateDto> listMandatesByDelegate(String internaluserId, String mandateId,
                                                           CxTypeAuthFleet xPagopaPnCxType, List<String> groups) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        log.info("listing private mandates by delegate for internaluserId={} mandateId={}", internaluserId, mandateId);
        return mandateDao.listMandatesByDelegate(internaluserId, StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE), mandateId, xPagopaPnCxType, groups)
                .map(mandateEntityInternalMandateDtoMapper::toDto)
                .doOnNext(mand -> log.info("listMandatesByDelegate found mandate={}", mand));
    }

    public Flux<InternalMandateDto> listMandatesByDelegator(String internaluserId,
                                                            String mandateId,
                                                            CxTypeAuthFleet xPagopaPnCxType,
                                                            List<String> cxGroups,
                                                            String cxRole,
                                                            DelegateType delegateType) {
        // nelle invocazioni tra servizi mi interessano SEMPRE solo le deleghe ATTIVE
        log.info("listing private mandates by delegate for internaluserId={} mandateId={}", internaluserId, mandateId);

        Integer status = StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE);
        return PgUtils.validaAccessoOnlyAdmin(xPagopaPnCxType, cxRole, cxGroups)
                .flatMapMany(obj -> mandateDao.listMandatesByDelegator(internaluserId, status, mandateId, delegateType))
                .map(mandateEntityInternalMandateDtoMapper::toDto)
                .doOnNext(mand -> log.info("listMandatesByDelegator found mandate={}", mand));
    }
}
