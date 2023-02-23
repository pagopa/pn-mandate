package it.pagopa.pn.mandate.services.mandate.v1;

import it.pagopa.pn.api.dto.events.EventType;
import it.pagopa.pn.commons.utils.ValidateUtils;
import it.pagopa.pn.mandate.exceptions.*;
import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.BaseRecipientDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.DenominationDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.MandateDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.RecipientTypeDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaSummaryDto;
import it.pagopa.pn.mandate.middleware.db.DelegateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDaoIT;
import it.pagopa.pn.mandate.middleware.db.entities.DelegateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.msclient.PnDataVaultClient;
import it.pagopa.pn.mandate.middleware.msclient.PnInfoPaClient;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.*;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class MandateServiceTest {

    private final Duration d = Duration.ofMillis(3000);

    private static final String PG_ADMIN_ROLE = "admin";

    @InjectMocks
    private MandateService mandateService;

    @Mock
    private MandateEntityMandateDtoMapper mapper;

    @Mock
    private MandateDao mandateDao;

    @Mock
    private DelegateDao delegateDao;

    @Mock
    UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper;

    @Mock
    PnInfoPaClient pnInfoPaClient;

    @Mock
    PnDataVaultClient pnDatavaultClient;

    @Mock
    ValidateUtils validateUtils;

    @Mock
    SqsService sqsService;

    @Test
    void acceptMandatePGNotAuthorized() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(mandateEntity.getValidationcode());

        Mono<MandateEntity> objectMono = mandateService.acceptMandate(mandateEntity.getMandateId(),
                Mono.just(acceptRequestDto), mandateEntity.getDelegate(), CxTypeAuthFleet.PG, new ArrayList<>(), "operator");

        //When
        Assertions.assertThrows(PnForbiddenException.class, objectMono::block);
    }

    @Test
    void acceptMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(mandateEntity.getValidationcode());
        when(mandateDao.acceptMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(mandateEntity));

        //When
        assertDoesNotThrow(() -> {
            mandateService.acceptMandate(mandateEntity.getMandateId(),
                            Mono.just(acceptRequestDto), mandateEntity.getDelegate(), CxTypeAuthFleet.PF, null, null)
                    .block(d);
        });

        //Then
        verifyNoInteractions(sqsService);
    }

    @Test
    void acceptMandatePG() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        mandateEntity.setDelegateisperson(false); // PG
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(mandateEntity.getValidationcode());
        when(mandateDao.acceptMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(mandateEntity));
        when(sqsService.sendToDelivery(mandateEntity, EventType.MANDATE_ACCEPTED))
                .thenReturn(Mono.just(SendMessageResponse.builder().build()));

        //When
        assertDoesNotThrow(() -> {
            mandateService.acceptMandate(mandateEntity.getMandateId(),
                            Mono.just(acceptRequestDto), mandateEntity.getDelegate(), CxTypeAuthFleet.PG, null, PG_ADMIN_ROLE)
                    .block(d);
        });

        //Then
        verify(sqsService).sendToDelivery(mandateEntity, EventType.MANDATE_ACCEPTED);
    }

    @Test
    void acceptMandateFailVercode() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(null);

        when(mandateDao.acceptMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Mono.empty());

        //When
        Mono<MandateEntity> mono = mandateService.acceptMandate(mandateEntity.getMandateId(),
                Mono.just(acceptRequestDto), mandateEntity.getDelegate(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidVerificationCodeException.class, () -> mono.block(d));

        //Then
        // nothing, basta che non ci sia eccezione
    }


    @Test
    void acceptMandateFailWrongVercode() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode("44444");

        when(mandateDao.acceptMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenThrow(new PnInvalidVerificationCodeException());

        //When
        Mono<MandateEntity> mono = mandateService.acceptMandate(mandateEntity.getMandateId(),
                Mono.just(acceptRequestDto), mandateEntity.getDelegate(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidVerificationCodeException.class, () -> mono.block(d));

        //Then
        // nothing, basta che non ci sia eccezione
    }


    @Test
    void acceptMandateFailWrongMandateId() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode("44444");

        when(mandateDao.acceptMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenThrow(new PnMandateNotFoundException());

        //When
        Mono<MandateEntity> mono = mandateService.acceptMandate(mandateEntity.getMandateId(),
                Mono.just(acceptRequestDto), mandateEntity.getDelegate(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnMandateNotFoundException.class, () -> mono.block(d));

        //Then
        // nothing, basta che non ci sia eccezione
    }


    @Test
    void acceptMandateFailMandateid() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(mandateEntity.getValidationcode());

        when(mandateDao.acceptMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Mono.empty());

        //When
        Mono<AcceptRequestDto> monodto = Mono.just(acceptRequestDto);
        String delegate = mandateEntity.getDelegate();
        Mono<MandateEntity> mono = mandateService.acceptMandate(null, monodto, delegate, CxTypeAuthFleet.PF, null, null);
        Assertions.assertThrows(PnMandateNotFoundException.class, () -> mono.block(d));

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void countMandatesByDelegatePGNotAuthorized() {
        //Given
        DelegateEntity delegateEntity = new DelegateEntity();
        delegateEntity.setPendingcount(5);
        MandateCountsDto dto = new MandateCountsDto();
        dto.setValue(delegateEntity.getPendingcount());

        when(delegateDao.countMandates(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Mono.just(delegateEntity));
        when(userEntityMandateCountsDtoMapper.toDto(Mockito.same(delegateEntity))).thenReturn(dto);

        //When
        Mono<MandateCountsDto> result = mandateService.countMandatesByDelegate(MandateDto.StatusEnum.PENDING.getValue(), "fake", CxTypeAuthFleet.PG, null, "operator");

        //Then
        Assertions.assertThrows(PnForbiddenException.class, result::block);
    }

    @Test
    void countMandatesByDelegate() {
        //Given
        DelegateEntity delegateEntity = new DelegateEntity();
        delegateEntity.setPendingcount(5);
        MandateCountsDto dto = new MandateCountsDto();
        dto.setValue(delegateEntity.getPendingcount());

        when(delegateDao.countMandates(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Mono.just(delegateEntity));
        when(userEntityMandateCountsDtoMapper.toDto(Mockito.same(delegateEntity))).thenReturn(dto);

        //When
        MandateCountsDto result = mandateService.countMandatesByDelegate(MandateDto.StatusEnum.PENDING.getValue(), "fake", CxTypeAuthFleet.PF, null, null).block(d);

        //Then
        assertNotNull(result);
        assertEquals(delegateEntity.getPendingcount(), result.getValue());
    }

    @Test
    void countMandatesByDelegateInvalid1() {
        //Given
        DelegateEntity delegateEntity = new DelegateEntity();
        delegateEntity.setPendingcount(5);
        MandateCountsDto dto = new MandateCountsDto();
        dto.setValue(delegateEntity.getPendingcount());

        when(delegateDao.countMandates(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Mono.just(delegateEntity));
        when(userEntityMandateCountsDtoMapper.toDto(Mockito.same(delegateEntity))).thenReturn(dto);

        //When
        Assertions.assertThrows(PnUnsupportedFilterException.class, () -> mandateService.countMandatesByDelegate(null, "fake", CxTypeAuthFleet.PF, null, null));

    }

    @Test
    void countMandatesByDelegateInvalid2() {
        //Given
        DelegateEntity delegateEntity = new DelegateEntity();
        delegateEntity.setPendingcount(5);
        MandateCountsDto dto = new MandateCountsDto();
        dto.setValue(delegateEntity.getPendingcount());

        when(delegateDao.countMandates(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Mono.just(delegateEntity));
        when(userEntityMandateCountsDtoMapper.toDto(Mockito.same(delegateEntity))).thenReturn(dto);

        //When
        String status = MandateDto.StatusEnum.ACTIVE.getValue();
        Assertions.assertThrows(PnUnsupportedFilterException.class, () -> mandateService.countMandatesByDelegate(status, "fake", CxTypeAuthFleet.PF, null, null));

    }

    @Test
    void createMandatePGNotAuthorized() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MandateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        Mono<MandateDto> result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegator(), entity.getDelegatorUid(), CxTypeAuthFleet.PG, null, "operator");

        //Then
        Assertions.assertThrows(PnForbiddenException.class, result::block);
    }

    @Test
    void createMandate() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MandateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateDtoDto> resgetmandatesbyid = new ArrayList<>();
        MandateDtoDto mandateDtoDto = new MandateDtoDto();
        mandateDtoDto.mandateId(entity.getMandateId());
        DenominationDtoDto denominationDtoDto = new DenominationDtoDto();
        denominationDtoDto.setDestName("mario");
        denominationDtoDto.setDestSurname("rossi");
        mandateDtoDto.setInfo(denominationDtoDto);
        resgetmandatesbyid.add(mandateDtoDto);


        when(mandateDao.createMandate(Mockito.any())).thenReturn(Mono.just(entity));
        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegate()));
        when(pnDatavaultClient.updateMandateById(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just("OK"));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.fromIterable(resgetmandatesbyid));
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);
        when(validateUtils.validate(Mockito.anyString(), Mockito.anyBoolean())).thenReturn( true );

        //When
        MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegator(), entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null).block(d);

        //Then
        assertNotNull(result);
        assertNotNull(result.getMandateId());
        assertNotNull(result.getStatus());
        assertEquals(mandateDto.getVerificationCode(), result.getVerificationCode());
    }

    @Test
    void createMandate_DelegateIsPG() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        entity.setDelegateisperson(false);
        // MandateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setCompanyName("mario srl");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setCompanyName("mario srl");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateDtoDto> resgetmandatesbyid = new ArrayList<>();
        MandateDtoDto mandateDtoDto = new MandateDtoDto();
        mandateDtoDto.mandateId(entity.getMandateId());
        DenominationDtoDto denominationDtoDto = new DenominationDtoDto();
        denominationDtoDto.setDestBusinessName("mario srl");
        mandateDtoDto.setInfo(denominationDtoDto);
        resgetmandatesbyid.add(mandateDtoDto);

        when(mandateDao.createMandate(Mockito.any())).thenReturn(Mono.just(entity));
        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegate()));
        when(pnDatavaultClient.updateMandateById(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just("OK"));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.fromIterable(resgetmandatesbyid));
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);
        when(validateUtils.validate(Mockito.anyString(), Mockito.anyBoolean())).thenReturn( true );

        //When
        MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegator(), entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null)
                .block(d);

        //Then
        assertNotNull(result);
        assertNotNull(result.getMandateId());
        assertNotNull(result.getStatus());
        assertEquals(mandateDto.getVerificationCode(), result.getVerificationCode());
    }

    @Test
    void createMandate_DelegatorIsPG() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MandateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateDtoDto> resgetmandatesbyid = new ArrayList<>();
        MandateDtoDto mandateDtoDto = new MandateDtoDto();
        mandateDtoDto.mandateId(entity.getMandateId());
        DenominationDtoDto denominationDtoDto = new DenominationDtoDto();
        denominationDtoDto.setDestName("mario");
        denominationDtoDto.setDestSurname("rossi");
        mandateDtoDto.setInfo(denominationDtoDto);
        resgetmandatesbyid.add(mandateDtoDto);

        when(mandateDao.createMandate(Mockito.any())).thenReturn(Mono.just(entity));
        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegate()));
        when(pnDatavaultClient.updateMandateById(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just("OK"));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.fromIterable(resgetmandatesbyid));
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);
        when(validateUtils.validate(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(true);

        //When
        MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegator(), entity.getDelegatorUid(), CxTypeAuthFleet.PG, null, "admin")
                .block(d);

        //Then
        assertNotNull(result);
        assertNotNull(result.getMandateId());
        assertNotNull(result.getStatus());
        assertEquals(mandateDto.getVerificationCode(), result.getVerificationCode());
    }

    @Test
    void createMandate_PG_piva() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        entity.setDelegateisperson(false);
        // MandateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setCompanyName("mario srl");
        mandateDto.getDelegate().setFiscalCode("15376371009");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setCompanyName("mario srl");
        mandateDtoRes.getDelegate().setFiscalCode("15376371009");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateDtoDto> resgetmandatesbyid = new ArrayList<>();
        MandateDtoDto mandateDtoDto = new MandateDtoDto();
        mandateDtoDto.mandateId(entity.getMandateId());
        DenominationDtoDto denominationDtoDto = new DenominationDtoDto();
        denominationDtoDto.setDestBusinessName("mario srl");
        mandateDtoDto.setInfo(denominationDtoDto);
        resgetmandatesbyid.add(mandateDtoDto);


        when(mandateDao.createMandate(Mockito.any())).thenReturn(Mono.just(entity));
        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegate()));
        when(pnDatavaultClient.updateMandateById(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just("OK"));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.fromIterable(resgetmandatesbyid));
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);
        when(validateUtils.validate(Mockito.anyString(), Mockito.anyBoolean())).thenReturn( true );

        //When
        MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegator(), entity.getDelegatorUid(),  CxTypeAuthFleet.PF, null, null)
                .block(d);

        //Then
        assertNotNull(result);
        assertNotNull(result.getMandateId());
        assertNotNull(result.getStatus());
        assertEquals(mandateDto.getVerificationCode(), result.getVerificationCode());
    }

    @Test
    void createMandateWithList() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MandateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        OrganizationIdDto organizationIdDto = new OrganizationIdDto();
        organizationIdDto.setUniqueIdentifier("abcd");
        mandateDto.getVisibilityIds().add(organizationIdDto);
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.getVisibilityIds().add(organizationIdDto);
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateDtoDto> resgetmandatesbyid = new ArrayList<>();
        MandateDtoDto mandateDtoDto = new MandateDtoDto();
        mandateDtoDto.mandateId(entity.getMandateId());
        DenominationDtoDto denominationDtoDto = new DenominationDtoDto();
        denominationDtoDto.setDestName("mario");
        denominationDtoDto.setDestSurname("rossi");
        mandateDtoDto.setInfo(denominationDtoDto);
        resgetmandatesbyid.add(mandateDtoDto);

        PaSummaryDto paSummaryDto = new PaSummaryDto();
        paSummaryDto.setId(organizationIdDto.getUniqueIdentifier());
        paSummaryDto.setName("nome");


        when(mandateDao.createMandate(Mockito.any())).thenReturn(Mono.just(entity));
        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegate()));
        when(pnDatavaultClient.updateMandateById(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just("OK"));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.fromIterable(resgetmandatesbyid));
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(paSummaryDto)));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);
        when(validateUtils.validate(Mockito.anyString(), Mockito.anyBoolean())).thenReturn( true );

        //When
        MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegator(), entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null)
                .block(d);

        //Then
        assertNotNull(result);
        assertNotNull(result.getMandateId());
        assertNotNull(result.getStatus());
        assertEquals(mandateDto.getVerificationCode(), result.getVerificationCode());
        assertEquals(paSummaryDto.getName(), result.getVisibilityIds().get(0).getName());
    }


    @Test
    void createMandateWithListMany() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MandateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        OrganizationIdDto organizationIdDto = new OrganizationIdDto();
        organizationIdDto.setUniqueIdentifier("abcd");
        mandateDto.getVisibilityIds().add(organizationIdDto);
        OrganizationIdDto organizationIdDto1 = new OrganizationIdDto();
        organizationIdDto1.setUniqueIdentifier("fghi");
        mandateDto.getVisibilityIds().add(organizationIdDto1);

        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.getVisibilityIds().add(organizationIdDto);
        mandateDtoRes.getVisibilityIds().add(organizationIdDto1);
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateDtoDto> resgetmandatesbyid = new ArrayList<>();
        MandateDtoDto mandateDtoDto = new MandateDtoDto();
        mandateDtoDto.mandateId(entity.getMandateId());
        DenominationDtoDto denominationDtoDto = new DenominationDtoDto();
        denominationDtoDto.setDestName("mario");
        denominationDtoDto.setDestSurname("rossi");
        mandateDtoDto.setInfo(denominationDtoDto);
        resgetmandatesbyid.add(mandateDtoDto);

        PaSummaryDto paSummaryDto = new PaSummaryDto();
        paSummaryDto.setId(organizationIdDto.getUniqueIdentifier());
        paSummaryDto.setName("nome");
        PaSummaryDto paSummaryDto1 = new PaSummaryDto();
        paSummaryDto1.setId(organizationIdDto1.getUniqueIdentifier());
        paSummaryDto1.setName("nome2");


        when(mandateDao.createMandate(Mockito.any())).thenReturn(Mono.just(entity));
        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegate()));
        when(pnDatavaultClient.updateMandateById(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just("OK"));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.fromIterable(resgetmandatesbyid));
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(paSummaryDto, paSummaryDto1)));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);
        when(validateUtils.validate(Mockito.anyString(), Mockito.anyBoolean())).thenReturn( true );

        //When
        MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegator(), entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null)
                .block(d);

        //Then
        assertNotNull(result);
        assertNotNull(result.getMandateId());
        assertNotNull(result.getStatus());
        assertEquals(mandateDto.getVerificationCode(), result.getVerificationCode());
        assertEquals(paSummaryDto.getName(), result.getVisibilityIds().get(0).getName());
        assertEquals(paSummaryDto1.getName(), result.getVisibilityIds().get(1).getName());
    }

    @Test
    void createMandateFailFiscalcode() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("FAKEFAKEFAKE");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        Mono<MandateDto> monodto = Mono.just(mandateDto);
        String delegate = entity.getDelegate();
        Mono<MandateDto> mono = mandateService.createMandate(monodto, delegate, entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidInputException.class, () -> mono.block(d));

        //Then
        //nothing
    }

    @Test
    void createMandateFailFiscalcode_PG() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setCompanyName("mario srl");
        mandateDto.getDelegate().setFiscalCode("FAKEFAKEFAKE");
        mandateDto.getDelegate().setPerson(false);

        //When
        Mono<MandateDto> monodto = Mono.just(mandateDto);
        String delegate = entity.getDelegate();
        Mono<MandateDto> mono = mandateService.createMandate(monodto, delegate, entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidInputException.class, () -> mono.block(d));

        //Then
        //nothing
    }

    @Test
    void createMandateFailFiscalcodeNull() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode(null);
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        Mono<MandateDto> monodto = Mono.just(mandateDto);
        String delegate = entity.getDelegate();
        Mono<MandateDto> mono = mandateService.createMandate(monodto, delegate, entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidInputException.class, () -> mono.block(d));

        //Then
        //nothing
    }

    @Test
    void createMandateFailVercodeNull() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(null);
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        Mono<MandateDto> monodto = Mono.just(mandateDto);
        String delegate = entity.getDelegate();
        Mono<MandateDto> mono = mandateService.createMandate(monodto, delegate, entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidInputException.class, () -> mono.block(d));

        //Then
        //nothing
    }

    @Test
    void createMandateFailVercodeInvalidformat() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode("123456");
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        Mono<MandateDto> monodto = Mono.just(mandateDto);
        String delegate = entity.getDelegate();
        Mono<MandateDto> mono = mandateService.createMandate(monodto, delegate, entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidInputException.class, () -> mono.block(d));

        //Then
        //nothing
    }


    @Test
    void createMandateFailNoValidTo() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);

        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(null);
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        Mono<MandateDto> mono = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegate(), entity.getDelegatorUid(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnInvalidInputException.class, () -> mono.block(d));

        //Then
        //nothing
    }


    @Test
    void createMandateFailSameUserDelegateDelegator() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);

        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(null);
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.setDateto(Instant.now().toString());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegator()));
        when(validateUtils.validate(Mockito.anyString(), Mockito.anyBoolean())).thenReturn( true );

        //When
        Mono<MandateDto> mono = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegatorUid(), entity.getDelegator(), CxTypeAuthFleet.PF, null, null);
        assertThrows(PnMandateByHimselfException.class, () -> mono.block(d));

        //Then
        //nothing
    }

    @Test
    void listMandatesByDelegate() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateEntity> listFromDb = new ArrayList<>();
        listFromDb.add(entity);

        when(mandateDao.listMandatesByDelegate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(listFromDb));
        when(pnDatavaultClient.getRecipientDenominationByInternalId(Mockito.any())).thenReturn(Flux.empty());
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.empty());
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        List<MandateDto> result = mandateService.listMandatesByDelegate(null, entity.getDelegate(), CxTypeAuthFleet.PF, null, null)
                .collectList().block(d);

        //Then
        assertNotNull(result);

        assertEquals(1, result.size());
    }

    @Test
    void listMandatesByDelegateWithPas() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        OrganizationIdDto organizationIdDto = new OrganizationIdDto();
        organizationIdDto.setUniqueIdentifier("abcd");
        mandateDto.getVisibilityIds().add(organizationIdDto);
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.getVisibilityIds().add(organizationIdDto);
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateEntity> listFromDb = new ArrayList<>();
        listFromDb.add(entity);

        when(mandateDao.listMandatesByDelegate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(listFromDb));
        when(pnDatavaultClient.getRecipientDenominationByInternalId(Mockito.any())).thenReturn(Flux.empty());
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.empty());
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        List<MandateDto> result = mandateService.listMandatesByDelegate(null, entity.getDelegate(), CxTypeAuthFleet.PF, null, null)
                .collectList().block(d);

        //Then
        assertNotNull(result);

        assertEquals(1, result.size());
    }

    @Test
    void listMandatesByDelegateWithDenominations() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        OrganizationIdDto organizationIdDto = new OrganizationIdDto();
        organizationIdDto.setUniqueIdentifier("abcd");
        mandateDto.getVisibilityIds().add(organizationIdDto);
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.getVisibilityIds().add(organizationIdDto);
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());
        mandateDtoRes.setDelegator(new UserDto());


        List<MandateEntity> listFromDb = new ArrayList<>();
        listFromDb.add(entity);

        List<BaseRecipientDtoDto> denominations = new ArrayList<>();
        denominations.add(new BaseRecipientDtoDto());
        denominations.get(0).setInternalId(entity.getDelegator());
        denominations.get(0).setDenomination("test user");
        denominations.get(0).setRecipientType(RecipientTypeDto.PF);
        denominations.get(0).setTaxId("TAXIDDELEGATOR");

        when(mandateDao.listMandatesByDelegate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(listFromDb));
        when(pnDatavaultClient.getRecipientDenominationByInternalId(Mockito.any())).thenReturn(Flux.fromIterable(denominations));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.empty());
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        List<MandateDto> result = mandateService.listMandatesByDelegate(null, entity.getDelegate(), CxTypeAuthFleet.PF, null, null)
                .collectList().block(d);

        //Then
        assertNotNull(result);

        assertEquals(1, result.size());
    }

    @Test
    void listMandatesByDelegateInvalid1() {
        //Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateEntity> listFromDb = new ArrayList<>();
        listFromDb.add(entity);

        when(mandateDao.listMandatesByDelegate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(listFromDb));
        when(pnDatavaultClient.getRecipientDenominationByInternalId(Mockito.any())).thenReturn(Flux.empty());
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.empty());
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        String delegate = entity.getDelegate();
        Assertions.assertThrows(PnUnsupportedFilterException.class, () -> mandateService.listMandatesByDelegate("INVALID", delegate, CxTypeAuthFleet.PF, null, null));
    }

    @Test
    void listMandatesByDelegator() {
//Given
        MandateEntity entity = MandateDaoIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(DateUtils.formatDate(entity.getValidto()));
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateEntity> listFromDb = new ArrayList<>();
        listFromDb.add(entity);

        when(mandateDao.listMandatesByDelegator(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.fromIterable(listFromDb));
        when(pnDatavaultClient.getRecipientDenominationByInternalId(Mockito.any())).thenReturn(Flux.empty());
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.empty());
        when(pnInfoPaClient.getManyPa(Mockito.anyList())).thenReturn(Flux.fromIterable(List.of(new PaSummaryDto())));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        List<MandateDto> result = mandateService.listMandatesByDelegator(entity.getDelegate(), CxTypeAuthFleet.PF, null, null)
                .collectList().block(d);

        //Then
        assertNotNull(result);

        assertEquals(1, result.size());
    }

    @Test
    void rejectMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.rejectMandate(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any()))
                .thenReturn(Mono.just("").then());

        //When
        assertDoesNotThrow(() -> {
            mandateService.rejectMandate(mandateEntity.getMandateId(), mandateEntity.getDelegate(), CxTypeAuthFleet.PF, null, null)
                    .block(d);
        });

        //Then
        verifyNoInteractions(sqsService);
    }

    @Test
    void rejectMandatePGNotAuthorized() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.rejectMandate(Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.empty());
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.empty());

        Mono<Void> resp = mandateService.rejectMandate(mandateEntity.getMandateId(), mandateEntity.getDelegate(), CxTypeAuthFleet.PG, "operator", new ArrayList<>());
        //When
        Assertions.assertThrows(PnForbiddenException.class, () -> resp.block(d));
    }

    @Test
    void rejectMandatePG() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        mandateEntity.setDelegateisperson(false); // PG

        when(sqsService.sendToDelivery(mandateEntity, EventType.MANDATE_REJECTED))
                .thenReturn(Mono.just(SendMessageResponse.builder().build()));
        when(mandateDao.rejectMandate(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(mandateEntity.getMandateId()))
                .thenReturn(Mono.just("").then());

        //When
        assertDoesNotThrow(() -> {
            mandateService.rejectMandate(mandateEntity.getMandateId(), mandateEntity.getDelegate(), CxTypeAuthFleet.PG, PG_ADMIN_ROLE, null)
                    .block(d);
        });

        //Then
        verify(sqsService).sendToDelivery(mandateEntity, EventType.MANDATE_REJECTED);
    }

    @Test
    void rejectMandateFailMandateId() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.rejectMandate(Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.empty());
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.empty());

        //When
        String delegate = mandateEntity.getDelegate();
        assertThrows(PnMandateNotFoundException.class, () -> mandateService.rejectMandate(null, delegate, CxTypeAuthFleet.PF, null, null));

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void revokeMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.revokeMandate(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any()))
                .thenReturn(Mono.just("").then());

        //When
        assertDoesNotThrow(() -> {
            mandateService.revokeMandate(mandateEntity.getMandateId(), mandateEntity.getDelegator(), CxTypeAuthFleet.PF, null, null)
                    .block(d);
        });

        //Then
        verifyNoInteractions(sqsService);
    }

    @Test
    void revokeMandatePGNotAuthorized() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.revokeMandate(Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.empty());

        Mono<Object> resp = mandateService.revokeMandate(mandateEntity.getMandateId(), mandateEntity.getDelegator(), CxTypeAuthFleet.PG, null, null);
        //When
        assertThrows(PnForbiddenException.class, () -> resp.block(d));
    }

    @Test
    void revokeMandatePG() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        mandateEntity.setDelegateisperson(false); // PG

        when(sqsService.sendToDelivery(mandateEntity, EventType.MANDATE_REVOKED))
                .thenReturn(Mono.just(SendMessageResponse.builder().build()));
        when(mandateDao.revokeMandate(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any()))
                .thenReturn(Mono.just("").then());

        //When
        assertDoesNotThrow(() -> {
            mandateService.revokeMandate(mandateEntity.getMandateId(), mandateEntity.getDelegator(), CxTypeAuthFleet.PG, PG_ADMIN_ROLE, null)
                    .block(d);
        });

        //Then
        verify(sqsService).sendToDelivery(mandateEntity, EventType.MANDATE_REVOKED);
    }

    @Test
    void revokeMandateFailMandateId() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.revokeMandate(Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.empty());

        //When
        String delegator = mandateEntity.getDelegator();
        assertThrows(PnMandateNotFoundException.class, () -> mandateService.revokeMandate(null, delegator, CxTypeAuthFleet.PF, null, null));

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void expireMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.expireMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any()))
                .thenReturn(Mono.just("").then());

        //When
        assertDoesNotThrow(() -> {
            mandateService.expireMandate(mandateEntity.getMandateId(), mandateEntity.getDelegatorUid(), mandateEntity.getDelegatorisperson() ? "PF" : "PG", mandateEntity.getDelegator())
                    .block(d);
        });

        //Then
        verifyNoInteractions(sqsService);
    }

    @Test
    void testExpireMandatePG() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        mandateEntity.setDelegateisperson(false); // PG

        when(sqsService.sendToDelivery(mandateEntity, EventType.MANDATE_EXPIRED))
                .thenReturn(Mono.just(SendMessageResponse.builder().build()));
        when(mandateDao.expireMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any()))
                .thenReturn(Mono.just("").then());

        //When
        assertDoesNotThrow(() -> {
            mandateService.expireMandate(mandateEntity.getMandateId(), mandateEntity.getDelegatorUid(), mandateEntity.getDelegateisperson() ? "PF" : "PG", mandateEntity.getDelegator())
                    .block(d);
        });

        //Then
        verify(sqsService).sendToDelivery(mandateEntity, EventType.MANDATE_EXPIRED);
    }

    @Test
    void expireMandateFailMandateId() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);

        when(mandateDao.expireMandate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(mandateEntity));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.empty());

        //When
        String delegator = mandateEntity.getDelegator();
        Assertions.assertThrows(PnMandateNotFoundException.class, () -> mandateService.expireMandate(null, "","", delegator));

        //Then
        // nothing, basta che non ci sia eccezione
    }
}