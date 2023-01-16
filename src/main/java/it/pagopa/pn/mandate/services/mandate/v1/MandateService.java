package it.pagopa.pn.mandate.services.mandate.v1;

import it.pagopa.pn.mandate.exceptions.*;
import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.BaseRecipientDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.DenominationDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.MandateDtoDto;
import it.pagopa.pn.mandate.middleware.db.DelegateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.msclient.PnDataVaultClient;
import it.pagopa.pn.mandate.middleware.msclient.PnInfoPaClient;
import it.pagopa.pn.mandate.model.mandate.SqsToDeliveryMessageDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.*;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.*;
import static it.pagopa.pn.mandate.utils.PgUtils.validaAccessoOnlyAdmin;
import static it.pagopa.pn.mandate.utils.PgUtils.validaAccessoOnlyGroupAdmin;


@Service
@Slf4j
public class MandateService {

    private static final String DELEGATE_FISCAL_CODE = "delegate.fiscalCode";
    private static final String VERIFICATION_CODE = "verificationCode";
    private static final String DELEGATE_PERSON = "delegate.person";
    private static final String DELEGATE = "delegate";
    private static final String DATE_TO = "dateTo";

    private final MandateDao mandateDao;
    private final DelegateDao userDao;
    private final MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper;
    private final UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper;
    private final PnInfoPaClient pnInfoPaClient;
    private final PnDataVaultClient pnDatavaultClient;
    private final SqsService sqsService;


    public MandateService(MandateDao mandateDao, DelegateDao userDao, MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper,
                          UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper, PnInfoPaClient pnInfoPaClient, PnDataVaultClient pnDatavaultClient, SqsService sqsService) {
        this.mandateDao = mandateDao;
        this.userDao = userDao;
        this.mandateEntityMandateDtoMapper = mandateEntityMandateDtoMapper;
        this.userEntityMandateCountsDtoMapper = userEntityMandateCountsDtoMapper;
        this.pnInfoPaClient = pnInfoPaClient;
        this.pnDatavaultClient = pnDatavaultClient;
        this.sqsService = sqsService;
    }

    /**
     * Accetta una delega
     *
     * @param mandateId        id della delega
     * @param acceptRequestDto dto accettazione delega
     * @param internaluserId   iuid del delegato
     * @return void
     */
    public Mono<MandateEntity> acceptMandate(String mandateId,
                                             Mono<AcceptRequestDto> acceptRequestDto,
                                             String internaluserId,
                                             CxTypeAuthFleet xPagopaPnCxType,
                                             List<String> cxGroups, String cxRole) {
        return validaAccessoOnlyAdmin(xPagopaPnCxType, cxRole, cxGroups)
                .flatMap(obj -> acceptRequestDto
                        .map(m -> {
                            if (m == null || m.getVerificationCode() == null)
                                throw new PnInvalidVerificationCodeException();

                            if (mandateId == null)
                                throw new PnMandateNotFoundException();

                            return m;
                        })
                        .flatMap(m -> {
                            try {
                                if (log.isInfoEnabled())
                                    log.info("accepting mandateobj:{} vercode:{}", mandateId, m);
                                return mandateDao
                                        .acceptMandate(internaluserId, mandateId, m.getVerificationCode(), m.getGroups(), xPagopaPnCxType)
                                        .flatMap(entity -> sendMessageToDelivery(mandateId, SqsToDeliveryMessageDto.Action.ACCEPT)
                                                .then(Mono.just(entity)));
                            } catch (Exception ex) {
                                throw Exceptions.propagate(ex);
                            }
                        })
                );
    }

    /**
     * Ritorna il numero di deleghe nello stato passato per il delegato
     *
     * @param status         stato per il filtro
     * @param internaluserId iuid del delegato
     * @return Totale deleghe nello stato richiesto
     */
    public Mono<MandateCountsDto> countMandatesByDelegate(String status,
                                                          String internaluserId,
                                                          CxTypeAuthFleet xPagopaPnCxType,
                                                          List<String> cxGroups,
                                                          String cxRole) {
        // per ora l'unico stato supportato è il pending, quindi il filtro non viene passato al count
        // Inoltre, ritorno un errore se status != pending
        if (status == null || !status.equals(StatusEnum.PENDING.getValue()))
            throw new PnUnsupportedFilterException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_ASSERTENUM, "status");
        return validaAccessoOnlyGroupAdmin(xPagopaPnCxType, cxRole, cxGroups)
                .flatMap(obj -> userDao.countMandates(internaluserId, xPagopaPnCxType, cxGroups)
                        .map(userEntityMandateCountsDtoMapper::toDto));
    }

    /**
     * Crea la delega
     *
     * @param mandateDto              oggetto delega
     * @param requesterInternaluserId iuid del delegante
     * @param requesterUserTypeIsPF   tipologia del delegante (PF/PG)
     * @return delega creata
     */
    public Mono<MandateDto> createMandate(Mono<MandateDto> mandateDto, final String requesterInternaluserId,
                                          final boolean requesterUserTypeIsPF, CxTypeAuthFleet cxTypeAuthFleet,
                                          List<String> groups, String role) {


        final String uuid = UUID.randomUUID().toString();
        return validaAccessoOnlyAdmin(cxTypeAuthFleet, role, groups)
                .flatMap(obj -> mandateDto
                        .map(this::validate)
                        .zipWhen(dto -> pnDatavaultClient.ensureRecipientByExternalId(dto.getDelegate().getPerson(), dto.getDelegate().getFiscalCode())
                                        .map(delegateInternaluserId -> {

                                            // qui posso controllare se delegante e delegato sono gli stessi (prima non li avevo disponibili)
                                            if (!CxTypeAuthFleet.PG.equals(cxTypeAuthFleet) && delegateInternaluserId.equals(requesterInternaluserId))
                                                throw new PnMandateByHimselfException();

                                            MandateEntity entity = mandateEntityMandateDtoMapper.toEntity(dto);
                                            entity.setDelegate(delegateInternaluserId);
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
                        .flatMap(ent -> {
                            if (!ent.getVisibilityIds().isEmpty())
                                return pnInfoPaClient
                                        .getOnePa(ent.getVisibilityIds().get(0).getUniqueIdentifier()) // per ora chiedo solo il primo...in futuro l'intera lista
                                        .flatMap(pa -> {
                                            ent.getVisibilityIds().get(0).setName(pa.getName());
                                            return Mono.just(ent);
                                        });
                            else
                                return Mono.just(ent);
                        })
                );
    }

    private MandateDto validate(MandateDto mandateDto) {
        // valida delegato
        if (mandateDto.getDelegate() == null)
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DELEGATE);
        if ((mandateDto.getDelegate().getFiscalCode() == null))
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DELEGATE_FISCAL_CODE);

        if ((mandateDto.getDelegate().getPerson() == null))
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DELEGATE_PERSON);

        if ((mandateDto.getDelegate().getPerson() && (mandateDto.getDelegate().getFirstName() == null || mandateDto.getDelegate().getLastName() == null))
                || (!mandateDto.getDelegate().getPerson() && mandateDto.getDelegate().getCompanyName() == null))
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER, DELEGATE);
        // codice verifica (5 caratteri)
        if (mandateDto.getVerificationCode() == null)
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, VERIFICATION_CODE);
        if (!mandateDto.getVerificationCode().matches("\\d\\d\\d\\d\\d"))
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_PATTERN, VERIFICATION_CODE);

        if (Boolean.TRUE.equals(mandateDto.getDelegate().getPerson())
                && !mandateDto.getDelegate().getFiscalCode().matches("[A-Za-z]{6}\\d{2}[A-Za-z]\\d{2}[A-Za-z]\\d{3}[A-Za-z]"))
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_PATTERN, DELEGATE_FISCAL_CODE);
        // le PG possono avere p.iva o CF!
        if (Boolean.FALSE.equals(mandateDto.getDelegate().getPerson())
                && !(mandateDto.getDelegate().getFiscalCode().matches("\\d{11}")
                || mandateDto.getDelegate().getFiscalCode().matches("[A-Za-z]{6}\\d{2}[A-Za-z]\\d{2}[A-Za-z]\\d{3}[A-Za-z]")))
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_PATTERN, DELEGATE_FISCAL_CODE);

        // la delega richiede la data di fine
        if (!StringUtils.hasText(mandateDto.getDateto()))
            throw new PnInvalidInputException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_REQUIRED, DATE_TO);

        return mandateDto;
    }

    /**
     * il metodo si occupa di tornare la lista delle deleghe per delegato.
     * Gli step sono:
     * (1) recuperare la lista delle entity da db
     * (2) pulisco le info provenienti da db dalle informazioni che non devo tornare (in questo caso, il validationcode)
     * (3) risolvere internalId del delegante (il delegato sono io, e non serve popolarlo) nel relativo microservizio
     * (4) risolvere eventuali deleghe con PA impostata, andando a recuperare il nome (da db recupero solo l'id) nel relativo microservizio
     *
     * @param status         stato per il filtro
     * @param internaluserId iuid del delegato
     * @return deleghe
     */
    public Flux<MandateDto> listMandatesByDelegate(String status,
                                                   String internaluserId,
                                                   CxTypeAuthFleet xPagopaPnCxType,
                                                   List<String> xPagopaPnCxGroups,
                                                   String xPagopaPnCxRole) {
        Integer iStatus = null;
        try {
            if (status != null && !status.equals(""))
                iStatus = StatusEnumMapper.intValfromValueConst(status);
        } catch (Exception e) {
            log.error("invalid state in filter");
            throw new PnUnsupportedFilterException(ERROR_CODE_PN_GENERIC_INVALIDPARAMETER_ASSERTENUM, "status");
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
                .flatMap(ent -> {                                           // (4)
                    if (!ent.getVisibilityIds().isEmpty())
                        return pnInfoPaClient
                                .getOnePa(ent.getVisibilityIds().get(0).getUniqueIdentifier()) // per ora chiedo solo il primo...in futuro l'intera lista
                                .flatMap(pa -> {
                                    ent.getVisibilityIds().get(0).setName(pa.getName());
                                    return Mono.just(ent);
                                });
                    else
                        return Mono.just(ent);
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
        return validaAccessoOnlyAdmin(pnCxType, pnCxRole, pnCxGroups) // (0)
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
                .flatMap(ent -> {                                           // (4)
                    if (!ent.getVisibilityIds().isEmpty()) {
                        return pnInfoPaClient
                                .getOnePa(ent.getVisibilityIds().get(0).getUniqueIdentifier()) // per ora chiedo solo il primo...in futuro l'intera lista
                                .flatMap(pa -> {
                                    ent.getVisibilityIds().get(0).setName(pa.getName());
                                    return Mono.just(ent);
                                });
                    } else {
                        return Mono.just(ent);
                    }
                });
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
            throw new PnMandateNotFoundException();
        }
        return validaAccessoOnlyAdmin(pnCxType, pnCxRole, pnCxGroups)
                .flatMap(o -> mandateDao.rejectMandate(internalUserId, mandateId))
                .then(this.pnDatavaultClient.deleteMandateById(mandateId))
                .then(Mono.defer(() -> sendMessageToDelivery(mandateId, SqsToDeliveryMessageDto.Action.REJECT)));
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
            throw new PnMandateNotFoundException();
        }
        return validaAccessoOnlyAdmin(pnCxType, pnCxRole, pnCxGroups)
                .flatMap(o -> mandateDao.revokeMandate(internalUserId, mandateId))
                .zipWhen(r -> this.pnDatavaultClient.deleteMandateById(mandateId), (r, d) -> d)
                .then(Mono.defer(() -> sendMessageToDelivery(mandateId, SqsToDeliveryMessageDto.Action.REVOKE)));
    }

    /**
     * Questo metodo non è pensato per essere usato dal FE, ma dalla callback proveniente da dynamostream
     * Si occupa di spostare la delega nello storico e toglierla dalla tabella principale
     *
     * @param mandateId      id della delega
     * @param internaluserId iuid del delegante
     * @return void
     */
    public Mono<Object> expireMandate(String mandateId, String internaluserId) {
        if (mandateId == null)
            throw new PnMandateNotFoundException();

        return mandateDao.expireMandate(internaluserId, mandateId)
                .zipWhen(r -> this.pnDatavaultClient.deleteMandateById(mandateId), (r, d) -> d)
                .then(Mono.defer(() -> sendMessageToDelivery(mandateId, SqsToDeliveryMessageDto.Action.EXPIRED)));
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

    private Mono<Void> sendMessageToDelivery(String mandateId, SqsToDeliveryMessageDto.Action action) {
        SqsToDeliveryMessageDto sqsToDeliveryMessageDto = SqsToDeliveryMessageDto.builder().action(action).mandateId(mandateId).build();
        return sqsService.push(sqsToDeliveryMessageDto).then();
    }
}
