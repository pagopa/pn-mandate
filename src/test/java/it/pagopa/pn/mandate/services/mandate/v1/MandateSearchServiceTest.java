package it.pagopa.pn.mandate.services.mandate.v1;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.BaseRecipientDtoDto;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaInfoDto;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.MandateDaoIT;
import it.pagopa.pn.mandate.middleware.db.PnLastEvaluatedKey;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.msclient.PnDataVaultClient;
import it.pagopa.pn.mandate.middleware.msclient.PnInfoPaClient;
import it.pagopa.pn.mandate.model.InputSearchMandateDto;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.pagopa.pn.mandate.model.PageResultDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.OrganizationIdDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@ContextConfiguration(classes = {MandateSearchService.class})
@ExtendWith(SpringExtension.class)
class MandateSearchServiceTest {

    private static final Duration D = Duration.ofMillis(3000);

    @MockBean
    private MandateDao mandateDao;
    @MockBean
    private MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper;
    @MockBean
    private PnDataVaultClient pnDataVaultClient;
    @MockBean
    private PnInfoPaClient pnInfoPaClient;

    @Autowired
    private MandateSearchService mandateSearchService;

    @Test
    void testSearchByDelegate() {
        InputSearchMandateDto searchDto = InputSearchMandateDto.builder()
                .delegateId("delegateId")
                .size(2)
                .maxPageNumber(1)
                .build();

        MandateEntity entity1 = MandateDaoIT.newMandate(false);
        entity1.setVisibilityIds(Set.of("paId1", "paId2"));
        MandateEntity entity2 = MandateDaoIT.newMandate(false);
        entity2.setDelegator(entity1.getDelegator());

        Page<MandateEntity> page1 = Page.create(List.of(entity1), Map.of("k", AttributeValue.builder().s("v").build()));
        Page<MandateEntity> page2 = Page.create(List.of(entity2));
        when(mandateDao.searchByDelegate(eq("delegateId"), isNull(), isNull(), isNull(), eq(3), any()))
                .thenReturn(Mono.just(page1))
                .thenReturn(Mono.just(page2));

        BaseRecipientDtoDto recipientDto1 = new BaseRecipientDtoDto();
        recipientDto1.setInternalId(entity1.getDelegator());
        recipientDto1.setDenomination("denomination");
        recipientDto1.setTaxId("taxId");
        when(pnDataVaultClient.getRecipientDenominationByInternalId(List.of(entity1.getDelegator())))
                .thenReturn(Flux.fromIterable(List.of(recipientDto1)));

        PaInfoDto paInfoDto1 = new PaInfoDto();
        paInfoDto1.setName("paName");
        when(pnInfoPaClient.getOnePa("paId1"))
                .thenReturn(Mono.just(paInfoDto1));
        when(pnInfoPaClient.getOnePa("paId2"))
                .thenReturn(Mono.empty());

        MandateDto dto1 = new MandateDto();
        dto1.setDelegator(new UserDto());
        OrganizationIdDto org1 = new OrganizationIdDto();
        org1.setUniqueIdentifier("paId1");
        OrganizationIdDto org2 = new OrganizationIdDto();
        org1.setUniqueIdentifier("paId2");
        dto1.setVisibilityIds(List.of(org1, org2));
        MandateDto dto2 = new MandateDto();
        dto2.setDelegator(new UserDto());
        when(mandateEntityMandateDtoMapper.toDto(entity1))
                .thenReturn(dto1);
        when(mandateEntityMandateDtoMapper.toDto(entity2))
                .thenReturn(dto2);

        PageResultDto<MandateDto, String> resultDto = mandateSearchService.searchByDelegate(searchDto, null)
                .block(D);
        assertNotNull(resultDto);
        assertEquals(2, resultDto.getPage().size());
        assertFalse(resultDto.isMore());
        assertTrue(resultDto.getNextPagesKey().isEmpty());
        assertEquals("denomination", resultDto.getPage().get(0).getDelegator().getDisplayName());
        assertEquals("taxId", resultDto.getPage().get(0).getDelegator().getFiscalCode());
    }

    @Test
    void testSearchByDelegateWithFilters() {
        InputSearchMandateDto searchDto = InputSearchMandateDto.builder()
                .delegateId("delegateId")
                .groups(List.of("G"))
                .delegatorIds(List.of("delegator"))
                .statuses(List.of(10, 20, 30))
                .size(1)
                .maxPageNumber(2)
                .build();

        PnLastEvaluatedKey lastEvaluatedKey = new PnLastEvaluatedKey();
        lastEvaluatedKey.setExternalLastEvaluatedKey("20");
        lastEvaluatedKey.setInternalLastEvaluatedKey(Map.of("k", AttributeValue.builder().s("v").build()));

        MandateEntity entity1 = MandateDaoIT.newMandate(false);
        MandateEntity entity2 = MandateDaoIT.newMandate(false);

        Page<MandateEntity> page1 = Page.create(Collections.emptyList());
        Page<MandateEntity> page2 = Page.create(List.of(entity1, entity2), Map.of("k", AttributeValue.builder().s("v").build()));
        when(mandateDao.searchByDelegate(eq("delegateId"), anyInt(), isNotNull(), isNotNull(), eq(12), any()))
                .thenReturn(Mono.just(page1))
                .thenReturn(Mono.just(page2));

        when(pnDataVaultClient.getRecipientDenominationByInternalId(any()))
                .thenReturn(Flux.empty());

        when(pnInfoPaClient.getOnePa(any()))
                .thenReturn(Mono.empty());

        when(mandateEntityMandateDtoMapper.toDto(any()))
                .thenCallRealMethod();

        PageResultDto<MandateDto, String> resultDto = mandateSearchService.searchByDelegate(searchDto, lastEvaluatedKey)
                .block(D);
        assertNotNull(resultDto);
        assertEquals(1, resultDto.getPage().size());
        assertTrue(resultDto.isMore());
        assertFalse(resultDto.getNextPagesKey().isEmpty());
        verify(mandateEntityMandateDtoMapper).toDto(entity1);
    }

    @Test
    void testSearchByDelegateEmpty() {
        InputSearchMandateDto searchDto = InputSearchMandateDto.builder()
                .delegateId("delegateId")
                .size(1)
                .maxPageNumber(1)
                .build();

        PnLastEvaluatedKey lastEvaluatedKey = new PnLastEvaluatedKey();
        lastEvaluatedKey.setExternalLastEvaluatedKey("20");
        lastEvaluatedKey.setInternalLastEvaluatedKey(Map.of("k", AttributeValue.builder().s("v").build()));

        Page<MandateEntity> page = Page.create(Collections.emptyList());
        when(mandateDao.searchByDelegate(anyString(), isNull(), any(), any(), anyInt(), any()))
                .thenReturn(Mono.just(page));

        PageResultDto<MandateDto, String> resultDto = mandateSearchService.searchByDelegate(searchDto, lastEvaluatedKey)
                .block(D);
        assertNotNull(resultDto);
        assertTrue(resultDto.getPage().isEmpty());
        assertFalse(resultDto.isMore());
        assertTrue(resultDto.getNextPagesKey().isEmpty());
        verifyNoInteractions(pnDataVaultClient);
        verifyNoInteractions(pnInfoPaClient);
        verifyNoInteractions(mandateEntityMandateDtoMapper);
    }
}
