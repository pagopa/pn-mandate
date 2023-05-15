package it.pagopa.pn.mandate.services.mandate.v1;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.*;
import it.pagopa.pn.mandate.utils.PgUtils;
import org.springframework.stereotype.Service;

import it.pagopa.pn.mandate.mapper.MandateEntityInternalMandateDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

@Service
@lombok.CustomLog
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

    public Flux<InternalMandateDto> listMandatesByDelegators(DelegateType delegateType,
                                                             List<String> delegateGroups,
                                                             Flux<MandateByDelegatorRequestDto> mandateByDelegatorRequestDto) {
        log.info("listing private mandates by delegators, delegateType={}, delegateGroups={}", delegateType, delegateGroups);
        Integer filterState = StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE);
        Set<String> filterDelegateGroups;
        if (CollectionUtils.isEmpty(delegateGroups)) {
            filterDelegateGroups = Collections.emptySet();
        } else {
            filterDelegateGroups = new HashSet<>(delegateGroups);
        }
        return mandateByDelegatorRequestDto.collectList()
                .flatMapMany(mandateDao::listMandatesByDelegators)
                .filter(mandate -> filterState.equals(mandate.getState())
                        && (DelegateType.PG != delegateType
                            || CollectionUtils.isEmpty(filterDelegateGroups)
                            || !Collections.disjoint(filterDelegateGroups, mandate.getGroups()))
                )
                .map(mandateEntityInternalMandateDtoMapper::toDto);
    }
}
