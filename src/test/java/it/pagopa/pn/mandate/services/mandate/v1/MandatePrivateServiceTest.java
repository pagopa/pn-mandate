package it.pagopa.pn.mandate.services.mandate.v1;

import it.pagopa.pn.mandate.mapper.MandateEntityInternalMandateDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDaoIT;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class MandatePrivateServiceTest {

    @InjectMocks
    private MandatePrivateService mandatePrivateService;

    @Mock
    private MandateEntityInternalMandateDtoMapper mapper;

    @Mock
    private MandateDao mandateDao;


    @Test
    void listMandatesByDelegate() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        List<MandateEntity> list = new ArrayList<>();
        list.add(mandateEntity);
        when(mandateDao.listMandatesByDelegate (Mockito.same(mandateEntity.getDelegate()), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(list));
        when(mapper.toDto(Mockito.same(mandateEntity))).thenReturn(new InternalMandateDto());

        //When
        List<InternalMandateDto> result = mandatePrivateService.listMandatesByDelegate(mandateEntity.getDelegate(), null).collectList().block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void listMandatesByDelegator() {
        //Given
        MandateEntity mandateEntity = MandateDaoIT.newMandate(true);
        List<MandateEntity> list = new ArrayList<>();
        list.add(mandateEntity);
        when(mandateDao.listMandatesByDelegator (Mockito.same(mandateEntity.getDelegate()), Mockito.any(), Mockito.any())).thenReturn(Flux.fromIterable(list));
        when(mapper.toDto(Mockito.same(mandateEntity))).thenReturn(new InternalMandateDto());

        //When
        List<InternalMandateDto> result = mandatePrivateService.listMandatesByDelegator(mandateEntity.getDelegate(), null).collectList().block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}