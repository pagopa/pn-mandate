package it.pagopa.pn.mandate.services.mandate.v1;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.AddressAndDenominationDtoDto;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.springframework.stereotype.Service;

import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.BaseRecipientDtoDto;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.MandateDtoDto;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.UserDao;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.microservice.PnDataVaultClient;
import it.pagopa.pn.mandate.middleware.microservice.PnInfoPaClient;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.UserDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum; 
import it.pagopa.pn.mandate.rest.utils.UnsupportedFilterException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class MandateService  {
   
    private final MandateDao mandateDao;
    private final UserDao userDao;
    private final MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper;
    private final UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper;
    private final PnInfoPaClient pnInfoPaClient;
    private final PnDataVaultClient pnDatavaultClient;

    public MandateService(MandateDao mandateDao, UserDao userDao, MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper,
        UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper, PnInfoPaClient pnInfoPaClient, PnDataVaultClient pnDatavaultClient) {
        this.mandateDao = mandateDao;
        this.userDao =userDao;
        this.mandateEntityMandateDtoMapper = mandateEntityMandateDtoMapper;
        this.userEntityMandateCountsDtoMapper = userEntityMandateCountsDtoMapper;
        this.pnInfoPaClient = pnInfoPaClient;
        this.pnDatavaultClient= pnDatavaultClient;
    }
    
    
    public Mono<Object> acceptMandate(String mandateId, Mono<AcceptRequestDto> acceptRequestDto,
            String internaluserId) {
        return acceptRequestDto
        .map(m -> {
            try{
                if (log.isInfoEnabled())
                    log.info("accepting mandate " + m);
                return mandateDao.acceptMandate(internaluserId, mandateId, m.getVerificationCode());                
            }catch(Exception ex)
            {
                throw Exceptions.propagate(ex);
            }            
        });     
    }

    public Mono<MandateCountsDto> countMandatesByDelegate(String status, String internaluserId) {
       // per ora l'unico stato supportato è il pending, quindi il filtro non viene passato al count
       // Inoltre, ritorno un errore se status != pending
       if (status == null || !status.equals(StatusEnum.PENDING.getValue()))
        throw new UnsupportedFilterException();
       return userDao.countMandates(internaluserId)                
                .map(userEntityMandateCountsDtoMapper::toDto);
    }

    
    public Mono<MandateDto> createMandate(Mono<MandateDto> mandateDto, String requesterInternaluserId, boolean requesterUserTypeIsPF) {
        return mandateDto
            .zipWhen(dto -> pnDatavaultClient.ensureRecipientByExternalId(dto.getDelegate().getPerson(), dto.getDelegate().getFiscalCode()),
            (dto, delegateInternaluserId) -> {
              MandateEntity entity = mandateEntityMandateDtoMapper.toEntity(dto);
              entity.setDelegate(delegateInternaluserId);
              return  entity;
            })
            .map(entity -> {
                    entity.setId(UUID.randomUUID().toString());
                    entity.setDelegator(requesterInternaluserId);
                    entity.setDelegatorisperson(requesterUserTypeIsPF);
                    entity.setState(StatusEnumMapper.intValfromStatus(StatusEnum.PENDING));
                    entity.setValidfrom(DateUtils.formatDate(LocalDate.now().minusDays(120)));
                    if (log.isInfoEnabled())
                            log.info("creating mandate uuid: {} iuid: {} iutype_isPF: {} validfrom: {}", entity.getId(), requesterInternaluserId, requesterUserTypeIsPF, entity.getValidfrom());
                    return entity;
            })
            .flatMap(mandateDao::createMandate)
            .map(r -> {
                r.setDelegator(null);
                return mandateEntityMandateDtoMapper.toDto(r);
            });
    }
    
    public Flux<MandateDto> listMandatesByDelegate(String status, String internaluserId) {
        // il metodo si occupa di tornare la lista delle deleghe per delegato.
        // Devo quindi:
        // (1) recuperare la lista delle entity da db
        // (2) pulisco le info provenienti da db dalle informazioni che non devo tornare (in questo caso, il validationcode)
        // (3) risolvere internalId del delegante (il delegato sono io, e non serve popolarlo) nel relativo microservizio
        // (4) risolvere eventuali deleghe con PA impostata, andando a recuperare il nome (da db recupero solo l'id) nel relativo microservizio

        Integer iStatus = null;
        if (status != null && !status.equals(""))
            iStatus = StatusEnumMapper.intValfromValueConst(status);

        return mandateDao.listMandatesByDelegate(internaluserId, iStatus)
                .map(ent -> {         
                    ent.setValidationcode(null);
                    return ent;        
                })                 
                .collectList()                                                        // (3)
                .zipWhen(entities -> {
                            // genero la lista degli id deleganti
                            List<String> internaluserIds = new ArrayList<>();
                            entities.forEach(ent -> internaluserIds.add(ent.getDelegator()));

                            // ritorno la lista
                            return this.pnDatavaultClient.getRecipientDenominationByInternalId(internaluserIds)
                                .collectMap(BaseRecipientDtoDto::getInternalId, BaseRecipientDtoDto::getDenomination);
                    },
                    (entities, userinfosdtos) -> {
                        List<MandateDto> dtos = new ArrayList<>();

                        for(MandateEntity ent : entities)
                        {
                            MandateDto dto = mandateEntityMandateDtoMapper.toDto(ent);
                            if (userinfosdtos.containsKey(ent.getDelegator()))
                            {
                                UserDto user = dto.getDelegate();
                                String denomination = userinfosdtos.get(ent.getDelegator());
                                if (user.getPerson())
                                    user.setDisplayName(denomination);
                                else
                                    user.setDisplayName(denomination);
                            }

                            dtos.add(dto);
                        }
                        return dtos;
                    })
                .flatMapMany(Flux::fromIterable)
                //.flatMap(ent -> mandateEntityMandateDtoMapper.toDto(Mono.just(ent)))                  
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
                })   ;

            
    }

    
    public Flux<MandateDto> listMandatesByDelegator(String internaluserId) {
        // il metodo si occupa di tornare la lista delle deleghe per delegato.
        // Devo quindi:
        // (1) recuperare la lista delle entity da db
        // (2) converto entity in dto
        // (3) recupero le info dei DELEGATI, eseguendo una richiesta con la lista degli id delle deleghe
        // (4) risolvere eventuali deleghe con PA impostata, andando a recuperare il nome (da db recupero solo l'id) nel relativo microservizio

        return mandateDao.listMandatesByDelegator(internaluserId, null)    // (1)
            .map(mandateEntityMandateDtoMapper::toDto)  // (2)
            .collectList()                                                        // (3)
            .zipWhen(dtos -> {
                        // genero la lista degli id delega
                        List<String> mandateIds = new ArrayList<>();
                        dtos.forEach(dto -> mandateIds.add(dto.getMandateId()));
                               
                        // ritorno la lista
                        return this.pnDatavaultClient.getMandatesByIds(mandateIds)
                            .collectMap(MandateDtoDto::getMandateId, MandateDtoDto::getInfo);
                },
                (dtos, userinfosdtos) -> {

                    for(MandateDto dto : dtos)
                    {
                        if (userinfosdtos.containsKey(dto.getMandateId()))
                        {
                            UserDto user = dto.getDelegate();
                            AddressAndDenominationDtoDto info = userinfosdtos.get(dto.getMandateId());
                            user.setCompanyName(info.getDestBusinessName());
                            user.setEmail(info.getValue());
                            user.setFirstName(info.getDestName());
                            user.setLastName(info.getDestSurname());
                            if (user.getPerson())
                                user.setDisplayName(info.getDestName() + " " + info.getDestSurname());
                            else
                                user.setDisplayName(info.getDestBusinessName());
                        }
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
            })         
        ;
    }

   
    public Mono<Object> rejectMandate(String mandateId, String internaluserId) {
        return mandateDao.rejectMandate(internaluserId, mandateId);
    }

    
    public Mono<Object> revokeMandate(String mandateId, String internaluserId) {
        return mandateDao.revokeMandate(internaluserId, mandateId);
    }

     
}
