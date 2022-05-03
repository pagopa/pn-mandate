package it.pagopa.pn.mandate.services.mandate.v1;

import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.DenominationDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.MandateDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaInfoDto;
import it.pagopa.pn.mandate.middleware.db.DelegateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDaoTestIT;
import it.pagopa.pn.mandate.middleware.db.entities.DelegateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.msclient.PnDataVaultClient;
import it.pagopa.pn.mandate.middleware.msclient.PnInfoPaClient;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.UserDto;
import it.pagopa.pn.mandate.rest.utils.InvalidInputException;
import it.pagopa.pn.mandate.rest.utils.InvalidVerificationCodeException;
import it.pagopa.pn.mandate.rest.utils.MandateNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class MandateServiceTest {


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

    @Test
    void acceptMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(mandateEntity.getValidationcode());

        when(mandateDao.acceptMandate (Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));

        //When
        assertDoesNotThrow(() -> {
            Object result = mandateService.acceptMandate(mandateEntity.getMandateId(),
                            Mono.just(acceptRequestDto), mandateEntity.getDelegate()).block(Duration.ofMillis(3000));
                });

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void acceptMandateFailVercode() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(null);

        when(mandateDao.acceptMandate (Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));

        //When
        try {
            Object result = mandateService.acceptMandate(mandateEntity.getMandateId(),
                    Mono.just(acceptRequestDto), mandateEntity.getDelegate()).block(Duration.ofMillis(3000));
            fail("no InvalidVerificationCodeException thrown");
        } catch (InvalidVerificationCodeException e) {
            // nothing
        }

        //Then
        // nothing, basta che non ci sia eccezione
    }


    @Test
    void acceptMandateFailMandateid() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);
        AcceptRequestDto acceptRequestDto = new AcceptRequestDto();
        acceptRequestDto.setVerificationCode(mandateEntity.getValidationcode());

        when(mandateDao.acceptMandate (Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));

        //When
        try {
            Object result = mandateService.acceptMandate(null,
                    Mono.just(acceptRequestDto), mandateEntity.getDelegate()).block(Duration.ofMillis(3000));
            fail("no MandateNotFoundException thrown");
        } catch (MandateNotFoundException e) {
            // nothing
        }

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void countMandatesByDelegate() {
        //Given
        DelegateEntity delegateEntity = new DelegateEntity();
        delegateEntity.setPendingcount(5);
        MandateCountsDto dto = new MandateCountsDto();
        dto.setValue(delegateEntity.getPendingcount());

        when(delegateDao.countMandates (Mockito.anyString())).thenReturn(Mono.just(delegateEntity));
        when(userEntityMandateCountsDtoMapper.toDto(Mockito.same(delegateEntity))).thenReturn(dto);

        //When
        MandateCountsDto result = mandateService.countMandatesByDelegate(MandateDto.StatusEnum.PENDING.getValue(),  "fake").block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);
        assertEquals(delegateEntity.getPendingcount(), result.getValue());
    }

    @Test
    void createMandate() {
        //Given
        MandateEntity entity = MandateDaoTestIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(entity.getValidto());
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(entity.getValidfrom());
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(entity.getValidto());
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
        mandateDtoDto.setInfo(new DenominationDtoDto());
        mandateDtoDto.getInfo().setDestName("mario");
        mandateDtoDto.getInfo().setDestSurname("rossi");
        resgetmandatesbyid.add(mandateDtoDto);


        when(mandateDao.createMandate (Mockito.any())).thenReturn(Mono.just(entity));
        when(pnDatavaultClient.ensureRecipientByExternalId(Mockito.anyBoolean(), Mockito.anyString())).thenReturn(Mono.just(entity.getDelegate()));
        when(pnDatavaultClient.updateMandateById(Mockito.any(), Mockito.any(),  Mockito.any(), Mockito.any())).thenReturn(Mono.just("OK"));
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.fromIterable(resgetmandatesbyid));
        when(pnInfoPaClient.getOnePa(Mockito.anyString())).thenReturn(Mono.just(new PaInfoDto()));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegate(), true).block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);
        assertNotNull(result.getMandateId());
        assertNotNull(result.getStatus());
        assertEquals(mandateDto.getVerificationCode(), result.getVerificationCode());
    }

    @Test
    void createMandateFailFiscalcode() {
        //Given
        MandateEntity entity = MandateDaoTestIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(entity.getValidto());
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("FAKEFAKEFAKE");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        try {
            MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegate(), true).block(Duration.ofMillis(3000));
            fail("no InvalidInputException thrown");
        } catch (InvalidInputException e) {
            //nothing
        }

        //Then
        //nothing
    }

    @Test
    void createMandateFailFiscalcodeNull() {
        //Given
        MandateEntity entity = MandateDaoTestIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(entity.getValidto());
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode(null);
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        try {
            MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegate(), true).block(Duration.ofMillis(3000));
            fail("no InvalidInputException thrown");
        } catch (InvalidInputException e) {
            //nothing
        }

        //Then
        //nothing
    }

    @Test
    void createMandateFailVercodeNull() {
        //Given
        MandateEntity entity = MandateDaoTestIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(entity.getValidto());
        mandateDto.setVerificationCode(null);
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        try {
            MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegate(), true).block(Duration.ofMillis(3000));
            fail("no InvalidInputException thrown");
        } catch (InvalidInputException e) {
            //nothing
        }

        //Then
        //nothing
    }

    @Test
    void createMandateFailVercodeInvalidformat() {
        //Given
        MandateEntity entity = MandateDaoTestIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(entity.getValidto());
        mandateDto.setVerificationCode("123456");
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        //When
        try {
            MandateDto result = mandateService.createMandate(Mono.just(mandateDto), entity.getDelegate(), true).block(Duration.ofMillis(3000));
            fail("no InvalidInputException thrown");
        } catch (InvalidInputException e) {
            //nothing
        }

        //Then
        //nothing
    }

    @Test
    void listMandatesByDelegate() {
        //Given
        MandateEntity entity = MandateDaoTestIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(entity.getValidto());
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(entity.getValidfrom());
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(entity.getValidto());
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateEntity> listFromDb = new ArrayList<>();
        listFromDb.add(entity);

        when(mandateDao.listMandatesByDelegate (Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(listFromDb));
        when(pnDatavaultClient.getRecipientDenominationByInternalId(Mockito.any())).thenReturn(Flux.empty());
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.empty());
        when(pnInfoPaClient.getOnePa(Mockito.anyString())).thenReturn(Mono.just(new PaInfoDto()));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        List<MandateDto> result = mandateService.listMandatesByDelegate(null, entity.getDelegate()).collectList().block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);

        assertEquals(1, result.size());
    }

    @Test
    void listMandatesByDelegator() {
//Given
        MandateEntity entity = MandateDaoTestIT.newMandate(true);
        // MAndateDto come proviene da FE quindi senza alcune info
        final MandateDto mandateDto = new MandateDto();
        mandateDto.setDateto(entity.getValidto());
        mandateDto.setVerificationCode(entity.getValidationcode());
        mandateDto.setVisibilityIds(new ArrayList<>());
        mandateDto.setDelegate(new UserDto());
        mandateDto.getDelegate().setFirstName("mario");
        mandateDto.getDelegate().setLastName("rossi");
        mandateDto.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDto.getDelegate().setPerson(entity.getDelegateisperson());

        final MandateDto mandateDtoRes = new MandateDto();
        mandateDtoRes.setMandateId(entity.getMandateId());
        mandateDtoRes.setDatefrom(entity.getValidfrom());
        mandateDtoRes.setStatus(MandateDto.StatusEnum.PENDING);
        mandateDtoRes.setDateto(entity.getValidto());
        mandateDtoRes.setVerificationCode(entity.getValidationcode());
        mandateDtoRes.setVisibilityIds(new ArrayList<>());
        mandateDtoRes.setDelegate(new UserDto());
        mandateDtoRes.getDelegate().setFirstName("mario");
        mandateDtoRes.getDelegate().setLastName("rossi");
        mandateDtoRes.getDelegate().setFiscalCode("RSSMRA85T10A562S");
        mandateDtoRes.getDelegate().setPerson(entity.getDelegateisperson());

        List<MandateEntity> listFromDb = new ArrayList<>();
        listFromDb.add(entity);

        when(mandateDao.listMandatesByDelegator (Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(listFromDb));
        when(pnDatavaultClient.getRecipientDenominationByInternalId(Mockito.any())).thenReturn(Flux.empty());
        when(pnDatavaultClient.getMandatesByIds(Mockito.any())).thenReturn(Flux.empty());
        when(pnInfoPaClient.getOnePa(Mockito.anyString())).thenReturn(Mono.just(new PaInfoDto()));
        when(mapper.toEntity(Mockito.any())).thenReturn(entity);
        when(mapper.toDto(Mockito.any())).thenReturn(mandateDtoRes);

        //When
        List<MandateDto> result = mandateService.listMandatesByDelegator(entity.getDelegate()).collectList().block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);

        assertEquals(1, result.size());
    }

    @Test
    void rejectMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);

        when(mandateDao.rejectMandate (Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.just("OK"));

        //When
        assertDoesNotThrow(() -> {mandateService.rejectMandate(mandateEntity.getMandateId(), mandateEntity.getDelegate()).block(Duration.ofMillis(3000));});

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void rejectMandateFailMandateId() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);

        when(mandateDao.rejectMandate (Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.just("OK"));

        //When
        try {
            Object result = mandateService.rejectMandate(null, mandateEntity.getDelegate()).block(Duration.ofMillis(3000));
            fail("no MandateNotFoundException thrown");
        } catch (MandateNotFoundException e) {
            //nothing
        }

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void revokeMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);

        when(mandateDao.revokeMandate (Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.just("OK"));

        //When
        assertDoesNotThrow(() -> {mandateService.revokeMandate(mandateEntity.getMandateId(), mandateEntity.getDelegator()).block(Duration.ofMillis(3000));});

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void revokeMandateFailMandateId() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);

        when(mandateDao.revokeMandate (Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.just("OK"));

        //When
        try {
            Object result = mandateService.revokeMandate(null, mandateEntity.getDelegator()).block(Duration.ofMillis(3000));
            fail("no MandateNotFoundException thrown");
        } catch (MandateNotFoundException e) {
            //nothing
        }

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void expireMandate() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);

        when(mandateDao.expireMandate (Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.just("OK"));

        //When
        assertDoesNotThrow(() -> {mandateService.expireMandate(mandateEntity.getMandateId(), mandateEntity.getDelegator()).block(Duration.ofMillis(3000));});

        //Then
        // nothing, basta che non ci sia eccezione
    }

    @Test
    void expireMandateFailMandateId() {
        //Given
        MandateEntity mandateEntity = MandateDaoTestIT.newMandate(true);

        when(mandateDao.expireMandate (Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.just(new Object()));
        when(pnDatavaultClient.deleteMandateById(Mockito.any())).thenReturn(Mono.just("OK"));

        //When
        try {
            Object result = mandateService.expireMandate(null, mandateEntity.getDelegator()).block(Duration.ofMillis(3000));
            fail("no MandateNotFoundException thrown");
        } catch (MandateNotFoundException e) {
            //nothing
        }

        //Then
        // nothing, basta che non ci sia eccezione
    }
}