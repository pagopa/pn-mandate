package it.pagopa.pn.mandate.services.mandate.v1;

import it.pagopa.pn.api.dto.events.EventType;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.exceptions.PnForbiddenException;
import it.pagopa.pn.mandate.exceptions.PnMandateNotFoundException;
import it.pagopa.pn.mandate.exceptions.PnUnsupportedFilterException;
import it.pagopa.pn.mandate.generated.openapi.msclient.datavault.v1.dto.BaseRecipientDtoDto;
import it.pagopa.pn.mandate.generated.openapi.msclient.datavault.v1.dto.DenominationDtoDto;
import it.pagopa.pn.mandate.generated.openapi.msclient.datavault.v1.dto.MandateDtoDto;
import it.pagopa.pn.mandate.generated.openapi.msclient.extregselfcare.v1.dto.PaSummaryDto;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.*;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.middleware.db.DelegateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.PnLastEvaluatedKey;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.msclient.PnDataVaultClient;
import it.pagopa.pn.mandate.middleware.msclient.PnInfoPaClient;
import it.pagopa.pn.mandate.model.InputSearchMandateDto;
import it.pagopa.pn.mandate.model.PageResultDto;
import it.pagopa.pn.mandate.services.mandate.utils.MandateValidationUtils;
import it.pagopa.pn.mandate.utils.DateUtils;
import it.pagopa.pn.mandate.utils.PgUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.*;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_ASSERTENUM;
import static it.pagopa.pn.mandate.utils.PgUtils.validaAccessoOnlyAdmin;
import static it.pagopa.pn.mandate.utils.PgUtils.validaAccessoOnlyGroupAdmin;

@Service
@lombok.CustomLog
public class MandateService {


    private final MandateDao mandateDao;
    private final DelegateDao userDao;
    private final MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper;
    private final UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper;
    private final PnInfoPaClient pnInfoPaClient;
    private final PnDataVaultClient pnDatavaultClient;
    private final SqsService sqsService;
    private final MandateValidationUtils validateUtils;
    private final MandateSearchService mandateSearchService;
    private final PnMandateConfig pnMandateConfig;

    public MandateService(MandateDao mandateDao,
                          DelegateDao userDao,
                          MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper,
                          UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper,
                          PnInfoPaClient pnInfoPaClient,
                          PnDataVaultClient pnDatavaultClient,
                          SqsService sqsService,
                          MandateValidationUtils validateUtils,
                          MandateSearchService mandateSearchService,
                          PnMandateConfig pnMandateConfig) {
        this.mandateDao = mandateDao;
        this.userDao = userDao;
        this.mandateEntityMandateDtoMapper = mandateEntityMandateDtoMapper;
        this.userEntityMandateCountsDtoMapper = userEntityMandateCountsDtoMapper;
        this.pnInfoPaClient = pnInfoPaClient;
        this.pnDatavaultClient = pnDatavaultClient;
        this.sqsService = sqsService;
        this.validateUtils = validateUtils;
        this.mandateSearchService = mandateSearchService;
        this.pnMandateConfig = pnMandateConfig;
    }

    /**
     * Accetta una delega
     *
     * @param mandateId         id della delega
     * @param acceptRequestDto  dto accettazione delega
     * @param internaluserId    iuid del delegato
     * @param xPagopaPnCxType   tipo dell'utente (PF, PG)
     * @param cxGroups          gruppi a cui appartiene l'utente
     * @param cxRole            ruolo dell'utente
     * @return void
     */
    public Mono<MandateEntity> acceptMandate(String mandateId,
                                             Mono<AcceptRequestDto> acceptRequestDto,
                                             String internaluserId,
                                             CxTypeAuthFleet xPagopaPnCxType,
                                             List<String> cxGroups, String cxRole) {
        return Mono.defer(() -> validaAccessoOnlyAdmin(xPagopaPnCxType, cxRole, cxGroups))
                .flatMap(obj -> acceptRequestDto)
                .map(m -> validateUtils.validateAcceptMandateRequest(mandateId, m))
                .flatMap(m -> validateUtils.validatePGGroups(internaluserId, m.getGroups()).thenReturn(m))
                .flatMap(m -> {
                    try {
                        if (log.isInfoEnabled())
                            log.info("accepting mandateobj:{} vercode:{}", mandateId, m);
                        return mandateDao
                                .acceptMandate(internaluserId, mandateId, m.getVerificationCode(), m.getGroups(), xPagopaPnCxType)
                                .flatMap(entity -> {
                                    if (Boolean.FALSE.equals(entity.getDelegateisperson())) {
                                        return sqsService.sendToDelivery(entity, EventType.MANDATE_ACCEPTED).then(Mono.just(entity));
                                    }
                                    return Mono.just(entity);
                                });
                    } catch (Exception ex) {
                        throw Exceptions.propagate(ex);
                    }
                });
    }

    public Mono<Void> updateMandate(String xPagopaPnCxId,
                                             CxTypeAuthFleet xPagopaPnCxType,
                                             String mandateId,
                                             List<String> xPagopaPnCxGroups,
                                             String xPagopaPnCxRole,
                                             Mono<UpdateRequestDto> updateRequestDto) {
        return validaAccessoOnlyAdmin(xPagopaPnCxType, xPagopaPnCxRole, xPagopaPnCxGroups)
                .flatMap(obj -> updateRequestDto)
                .flatMap(m -> validateUtils.validatePGGroups(xPagopaPnCxId, m.getGroups()).thenReturn(m))
                .flatMap(request -> {
                    log.info("updating mandate {}", mandateId);
                    Set<String> groups = null;
                    if (request.getGroups() != null && !request.getGroups().isEmpty()) {
                        groups = new HashSet<>(request.getGroups());
                    }
                    return mandateDao.updateMandate(xPagopaPnCxId, mandateId, groups)
                            .flatMap(tuple -> {
                                if (Boolean.FALSE.equals(tuple.getT2().getDelegateisperson())) {
                                    return sqsService.sendToDelivery(tuple.getT1(), tuple.getT2(), EventType.MANDATE_UPDATED).then();
                                }
                                return Mono.just(tuple.getT2()).then();
                            });
                });
    }

    /**
     * Ritorna il numero di deleghe nello stato passato per il delegato
     *
     * @param status            stato per il filtro
     * @param internaluserId    iuid del delegato
     * @param xPagopaPnCxType   tipo dell'utente (PF, PG)
     * @param cxGroups          gruppi a cui appartiene l'utente
     * @param cxRole            ruolo dell'utente
     *
     * @return Totale deleghe nello stato richiesto
     */
    public Mono<MandateCountsDto> countMandatesByDelegate(String status,
                                                          String internaluserId,
                                                          CxTypeAuthFleet xPagopaPnCxType,
                                                          List<String> cxGroups,
                                                          String cxRole) {

        return validateUtils.validateCountRequest(status)
                .then(Mono.defer(() -> validaAccessoOnlyGroupAdmin(xPagopaPnCxType, cxRole, cxGroups)))
                .flatMap(obj -> userDao.countMandates(internaluserId, xPagopaPnCxType, cxGroups)
                        .map(userEntityMandateCountsDtoMapper::toDto));
    }


    /**
     * Crea la delega
     *
     * @param mandateDto                oggetto delega
     * @param requesterInternaluserId   iuid del delegante
     * @param cxTypeAuthFleet           tipologia del delegante (PF/PG)
     * @param groups                    gruppi a cui appartiene l'utente
     * @param role                      ruolo dell'utente
     * @return delega creata
     */
    public Mono<MandateDto> createMandate(Mono<MandateDto> mandateDto,
                                          final String requesterUid,
                                          final String requesterInternaluserId,
                                          CxTypeAuthFleet cxTypeAuthFleet,
                                          List<String> groups,
                                          String role) {
        final String uuid = UUID.randomUUID().toString();
        final boolean requesterUserTypeIsPF = cxTypeAuthFleet == null || cxTypeAuthFleet.equals(CxTypeAuthFleet.PF);
        return Mono.defer(() -> validaAccessoOnlyAdmin(cxTypeAuthFleet, role, groups))
                .flatMap(obj -> mandateDto
                        .map(validateUtils::validateCreationRequest)
                        .zipWhen(dto -> pnDatavaultClient.ensureRecipientByExternalId(dto.getDelegate().getPerson(), dto.getDelegate().getFiscalCode())
                                        .map(delegateInternaluserId -> {

                                            // qui posso controllare se delegante e delegato sono gli stessi (prima non li avevo disponibili)
                                            validateUtils.validateCreationRequestHimself(cxTypeAuthFleet, requesterInternaluserId, delegateInternaluserId);

                                            MandateEntity entity = mandateEntityMandateDtoMapper.toEntity(dto);
                                            entity.setDelegate(delegateInternaluserId);
                                            entity.setDelegatorUid(requesterUid);
                                            entity.setMandateId(uuid);
                                            entity.setDelegator(requesterInternaluserId);
                                            entity.setDelegatorisperson(requesterUserTypeIsPF);
                                            entity.setState(StatusEnumMapper.intValfromStatus(StatusEnum.PENDING));
                                            entity.setValidfrom(DateUtils.atStartOfDay(ZonedDateTime.now().minusDays(120).toInstant()).toInstant());
                                            if (log.isInfoEnabled())
                                                log.info("creating mandate uuid: {} iuid: {} iutype_isPF: {} validfrom: {}",
                                                        entity.getMandateId(), requesterInternaluserId, requesterUserTypeIsPF, entity.getValidfrom());

                                            return entity;
                                        })
                                        .flatMap(ent -> pnDatavaultClient.updateMandateById(uuid, dto.getDelegate().getFirstName(),
                                                        dto.getDelegate().getLastName(), dto.getDelegate().getCompanyName())
                                                .then(Mono.just(ent)))
                                        .flatMap(mandateDao::createMandate)
                                , (ddto, entity) -> entity)
                        .map(r -> {
                            r.setDelegator(null);
                            return mandateEntityMandateDtoMapper.toDto(r);
                        })
                        .zipWhen(dto -> {
                                    // genero la lista degli id delega
                                    List<String> mandateIds = new ArrayList<>();
                                    mandateIds.add(dto.getMandateId());

                                    // ritorno la lista
                                    return this.pnDatavaultClient.getMandatesByIds(mandateIds)
                                            .collectMap(MandateDtoDto::getMandateId, MandateDtoDto::getInfo);
                                },
                                (dto, userinfosdtos) -> {
                                    if (userinfosdtos.containsKey(dto.getMandateId()))
                                        updateUserDto(dto.getDelegate(), userinfosdtos.get(dto.getMandateId()));

                                    return dto;
                                })
                        .flatMap(this::enrichWithPaInfos)
                );
    }


    /**
     * il metodo si occupa di tornare la lista delle deleghe per delegato.
     * Gli step sono:
     * (1) recuperare la lista delle entity da db
     * (2) pulisco le info provenienti da db dalle informazioni che non devo tornare (in questo caso, il validationcode)
     * (3) risolvere internalId del delegante (il delegato sono io, e non serve popolarlo) nel relativo microservizio
     * (4) risolvere eventuali deleghe con PA impostata, andando a recuperare il nome (da db recupero solo l'id) nel relativo microservizio
     *
     * @param status            stato per il filtro
     * @param internaluserId    iuid del delegato
     * @param xPagopaPnCxType   tipo dell'utente (PF, PG)
     * @param xPagopaPnCxGroups gruppi a cui appartiene l'utente
     * @param xPagopaPnCxRole   ruolo dell'utente
     * @return deleghe
     */
    public Flux<MandateDto> listMandatesByDelegate(String status,
                                                   String internaluserId,
                                                   CxTypeAuthFleet xPagopaPnCxType,
                                                   List<String> xPagopaPnCxGroups,
                                                   String xPagopaPnCxRole) {

        try {
            validateUtils.validateListMandatesByDelegateRequest(status, internaluserId, xPagopaPnCxType, xPagopaPnCxGroups, xPagopaPnCxRole);
        }catch(PnRuntimeException e){
            Flux.error(e);
        }

        Integer iStatus = null;
        if (status != null && !status.equals("")) {
            iStatus = convertStatusStringToInteger(status);
        }

        Integer finalIStatus = iStatus;
        return validaAccessoOnlyGroupAdmin(xPagopaPnCxType,xPagopaPnCxRole,xPagopaPnCxGroups)
                .flatMapMany(obj -> mandateDao.listMandatesByDelegate(internaluserId, finalIStatus, null, xPagopaPnCxType, xPagopaPnCxGroups))   // (1)
                .map(ent -> {
                    ent.setValidationcode(null);   // (2)
                    return ent;
                })
                .doOnNext(mand -> log.info("listMandatesByDelegate found mandate={}", mand))
                .collectList()                                                        // (3)
                .zipWhen(entities -> {
                            if (!entities.isEmpty()) {
                                // genero la lista degli id deleganti
                                List<String> internaluserIds = new ArrayList<>();
                                entities.forEach(ent -> internaluserIds.add(ent.getDelegator()));

                                // ritorno la lista
                                return this.pnDatavaultClient.getRecipientDenominationByInternalId(internaluserIds)
                                        .collectMap(BaseRecipientDtoDto::getInternalId, baseRecipientDtoDto -> baseRecipientDtoDto);
                            } else
                                return Mono.just(new HashMap<String, String>());
                        },
                        (entities, userinfosdtos) -> {
                            List<MandateDto> dtos = new ArrayList<>();

                            for (MandateEntity ent : entities) {
                                MandateDto dto = mandateEntityMandateDtoMapper.toDto(ent);
                                if (userinfosdtos.containsKey(ent.getDelegator())) {
                                    UserDto user = dto.getDelegator();
                                    BaseRecipientDtoDto baseRecipientDtoDto = ((BaseRecipientDtoDto) userinfosdtos.get(ent.getDelegator()));
                                    user.setDisplayName(baseRecipientDtoDto.getDenomination());
                                    user.setFiscalCode(baseRecipientDtoDto.getTaxId());
                                }
                                dtos.add(dto);
                            }
                            return dtos;
                        })
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::enrichWithPaInfos);
    }

    /**
     * Il metodo si occupa di tornare la lista delle deleghe dato il delegante. Opzionalmente, è possibile filtrare
     * per stati, gruppi e deleganti.
     *
     * @param requestDto    body della richiesta
     * @param size          dimensione della pagina
     * @param nextPageKey   next key per la paginazione
     * @param cxId          id del delegato
     * @param cxType        tipo del delegato
     * @param cxGroups      gruppi del delegato
     * @param cxRole        ruolo del delegato
     * @return              una lista paginata di deleghe che rispettano i filtri in ingresso
     */
    public Mono<SearchMandateResponseDto> searchByDelegate(Mono<SearchMandateRequestDto> requestDto,
                                                           Integer size,
                                                           String nextPageKey,
                                                           String cxId,
                                                           CxTypeAuthFleet cxType,
                                                           List<String> cxGroups,
                                                           String cxRole) {
        return validaAccessoOnlyGroupAdmin(cxType, cxRole, cxGroups)
                .flatMap(obj -> requestDto)
                .flatMap(request -> getInternalIdFromTaxId(request.getTaxId())
                        .map(internalIds -> {
                            List<Integer> statutes = convertStatusStringToInteger(request.getStatus());
                            InputSearchMandateDto searchDto = InputSearchMandateDto.builder()
                                    .delegateId(cxId)
                                    .statuses(statutes)
                                    .delegatorIds(internalIds)
                                    .size(size)
                                    .nextPageKey(nextPageKey)
                                    .build();
                            searchDto.setMaxPageNumber(pnMandateConfig.getMaxPageSize());
                            searchDto.setGroups(PgUtils.getGroupsForSecureFilter(request.getGroups(), cxGroups));
                            validateUtils.validateSearchRequest(searchDto);
                            log.debug("searchByDelegate filters: {}", searchDto);
                            return searchDto;
                        })
                        .flatMap(searchDto -> {
                            if (StringUtils.hasText(request.getTaxId()) && searchDto.getDelegatorIds().isEmpty()) {
                                // se ho passato un taxId e non ho trovato l'internalId corrispondente, ritorno una pagina vuota
                                return Mono.just(PageResultDto.<MandateDto, String>builder()
                                        .more(false)
                                        .page(Collections.emptyList())
                                        .nextPagesKey(Collections.emptyList())
                                        .build());
                            }
                            PnLastEvaluatedKey lastEvaluatedKey = convertLastEvaluatedKey(searchDto.getNextPageKey());
                            return mandateSearchService.searchByDelegate(searchDto, lastEvaluatedKey);
                        }))
                .map(result -> {
                    log.info("searchByDelegate size: {}, hasMore: {}, nextPagesKey: {}", result.getPage().size(), result.isMore(), result.getNextPagesKey());
                    SearchMandateResponseDto responseDto = new SearchMandateResponseDto();
                    responseDto.setMoreResult(result.isMore());
                    responseDto.setNextPagesKey(result.getNextPagesKey());
                    responseDto.setResultsPage(result.getPage());
                    return responseDto;
                });
    }

    /**
     * Il metodo si occupa di tornare la lista delle deleghe per delegante.
     * Gli step sono:
     * (0) validazione accesso per le PG (solo gli amministratori possono visualizzare le deleghe dove la PG è delegante)
     * (1) recuperare la lista delle entity da db
     * (2) converto entity in dto
     * (3) recupero le info dei DELEGATI, eseguendo una richiesta con la lista degli id delle deleghe
     * (4) risolvere eventuali deleghe con PA impostata, andando a recuperare il nome (da db recupero solo l'id) nel relativo microservizio
     *
     * @param internalUserId iuid del delegante
     * @param pnCxType       tipo di utente
     * @param pnCxRole       ruolo dell'utente
     * @param pnCxGroups     gruppi a cui appartiene l'utente
     * @return deleghe
     */
    public Flux<MandateDto> listMandatesByDelegator(String internalUserId, CxTypeAuthFleet pnCxType, List<String> pnCxGroups, String pnCxRole) {
        return Mono.defer(() -> validaAccessoOnlyAdmin(pnCxType, pnCxRole, pnCxGroups)) // (0)
                .flatMapMany(o -> mandateDao.listMandatesByDelegator(internalUserId, null, null, null))    // (1)
                .doOnNext(mand -> log.info("listMandatesByDelegator found mandate={}", mand))
                .map(mandateEntityMandateDtoMapper::toDto)  // (2)
                .collectList()                                                        // (3)
                .zipWhen(dtos -> {
                            if (!dtos.isEmpty()) {
                                // genero la lista degli id delega
                                List<String> mandateIds = new ArrayList<>();
                                dtos.forEach(dto -> mandateIds.add(dto.getMandateId()));

                                // ritorno la lista
                                return this.pnDatavaultClient.getMandatesByIds(mandateIds)
                                        .collectMap(MandateDtoDto::getMandateId, MandateDtoDto::getInfo);
                            } else {
                                return Mono.just(new HashMap<String, DenominationDtoDto>());
                            }
                        },
                        (dtos, userinfosdtos) -> {
                            for (MandateDto dto : dtos) {
                                if (userinfosdtos.containsKey(dto.getMandateId()))
                                    updateUserDto(dto.getDelegate(), userinfosdtos.get(dto.getMandateId()));
                            }
                            return dtos;
                        })
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::enrichWithPaInfos);
    }

    /**
     * Rifiuta una delega. Per una PG solo l'amministratore può rifiutare una delega.
     *
     * @param mandateId      id della delega
     * @param internalUserId iuid del delegato
     * @param pnCxRole       ruolo dell'utente
     * @param pnCxGroups     gruppi a cui appartiene l'utente
     * @param pnCxType       tipo di utente
     * @return void
     */
    public Mono<Void> rejectMandate(String mandateId, String internalUserId, CxTypeAuthFleet pnCxType, String pnCxRole, List<String> pnCxGroups) {
        if (mandateId == null) {
            return Mono.error(new PnMandateNotFoundException());
        }

        return Mono.defer(() -> validaAccessoOnlyAdmin(pnCxType, pnCxRole, pnCxGroups))
                .flatMap(o -> mandateDao.rejectMandate(internalUserId, mandateId))
                .flatMap(r -> pnDatavaultClient.deleteMandateById(mandateId).thenReturn(r))
                .flatMap(entity -> {
                    if (Boolean.FALSE.equals(entity.getDelegateisperson())) {
                        return sqsService.sendToDelivery(entity, EventType.MANDATE_REJECTED).then();
                    }
                    return Mono.just(entity).then();
                });
    }

    /**
     * Revoca una delega. Per una PG solo l'amministratore può revocare una delega.
     *
     * @param mandateId      id della delega
     * @param internalUserId iuid del delegante
     * @param pnCxRole       ruolo dell'utente
     * @param pnCxGroups     gruppi a cui appartiene l'utente
     * @param pnCxType       tipo di utente
     * @return void
     */
    public Mono<Object> revokeMandate(String mandateId, String internalUserId, CxTypeAuthFleet pnCxType, String pnCxRole, List<String> pnCxGroups) {
        if (mandateId == null) {
            return Mono.error(new PnMandateNotFoundException());
        }
        return validaAccessoOnlyAdmin(pnCxType, pnCxRole, pnCxGroups)
                .flatMap(o -> mandateDao.revokeMandate(internalUserId, mandateId))
                .flatMap(r -> pnDatavaultClient.deleteMandateById(mandateId).thenReturn(r))
                .flatMap(entity -> {
                    if (Boolean.FALSE.equals(entity.getDelegateisperson())) {
                        return sqsService.sendToDelivery(entity, EventType.MANDATE_REVOKED).thenReturn(entity);
                    }
                    return Mono.just(entity);
                });
    }

    /**
     * Questo metodo non è pensato per essere usato dal FE, ma dalla callback proveniente da dynamostream
     * Si occupa di spostare la delega nello storico e toglierla dalla tabella principale
     *
     * @param mandateId      id della delega
     * @param internaluserId iuid del delegante
     * @return void
     */
    public Mono<Object> expireMandate(String mandateId, String internaluserId, String uid, String cxType) {
        if (mandateId == null)
            throw new PnMandateNotFoundException();

        return mandateDao.expireMandate(internaluserId, uid, cxType, mandateId)
                .flatMap(r -> pnDatavaultClient.deleteMandateById(mandateId).thenReturn(r))
                .flatMap(entity -> {
                    if (Boolean.FALSE.equals(entity.getDelegateisperson())) {
                        return sqsService.sendToDelivery(entity, EventType.MANDATE_EXPIRED).thenReturn(entity);
                    }
                    return Mono.just(entity);
                });
    }

    private void updateUserDto(UserDto user, DenominationDtoDto info) {
        user.setCompanyName(info.getDestBusinessName());
        user.setFirstName(info.getDestName());
        user.setLastName(info.getDestSurname());
        if (Boolean.TRUE.equals(user.getPerson()))
            user.setDisplayName(info.getDestName() + " " + info.getDestSurname());
        else
            user.setDisplayName(info.getDestBusinessName());
    }

    private Mono<MandateDto> enrichWithPaInfos(MandateDto mandateDto){
        if (!mandateDto.getVisibilityIds().isEmpty())
            return pnInfoPaClient
                    .getManyPa(mandateDto.getVisibilityIds().stream().map(OrganizationIdDto::getUniqueIdentifier).toList())
                    .collectMap(PaSummaryDto::getId, PaSummaryDto::getName)
                    .map(paMap -> {
                        mandateDto.getVisibilityIds().forEach(organizationIdDto ->
                                organizationIdDto.setName(paMap.getOrDefault(organizationIdDto.getUniqueIdentifier(), null)));
                        return mandateDto;
                    });
        else
            return Mono.just(mandateDto);
    }

    private List<Integer> convertStatusStringToInteger(List<String> statutes) {
        if (CollectionUtils.isEmpty(statutes)) {
            return Collections.emptyList();
        }
        return statutes.stream()
                .map(this::convertStatusStringToInteger)
                .toList();
    }

    private Integer convertStatusStringToInteger(String status) {
        try {
            return StatusEnumMapper.intValfromValueConst(status);
        } catch (Exception e) {
            log.error("invalid status in filter", e);
            throw new PnUnsupportedFilterException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_ASSERTENUM, "status");
        }
    }

    private @Nullable PnLastEvaluatedKey convertLastEvaluatedKey(String nextPagesKey) {
        if (StringUtils.hasText(nextPagesKey)) {
            return PnLastEvaluatedKey.deserialize(nextPagesKey);
        }
        return null;
    }


    private Mono<List<String>> getInternalIdFromTaxId(String taxId) {
        if (StringUtils.hasText(taxId)) {
            if(taxId.length() == 11){
                return this.pnDatavaultClient.ensureRecipientByExternalId(false, taxId)
                        .map(Collections::singletonList);
            }else{
                return this.pnDatavaultClient.ensureRecipientByExternalId(true, taxId)
                        .concatWith(this.pnDatavaultClient.ensureRecipientByExternalId(false, taxId))
                        .collectList();
            }
        } else {
            return Mono.just(Collections.emptyList());
        }
    }
}
