package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.PnAuditLogBuilder;
import it.pagopa.pn.mandate.LocalStackTestConfig;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.exceptions.PnInvalidVerificationCodeException;
import it.pagopa.pn.mandate.exceptions.PnMandateAlreadyExistsException;
import it.pagopa.pn.mandate.exceptions.PnMandateNotFoundException;
import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateSupportEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.*;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Import(LocalStackTestConfig.class)
public class MandateDaoIT {

    private final Duration d = Duration.ofMillis(60000);

    @Autowired
    private MandateDao mandateDao;

    @Autowired
    PnMandateConfig cfg;

    @Autowired
    PnAuditLogBuilder pnAuditLogBuilder;

    @Autowired
    DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient;

    TestDao testDao;



    @BeforeEach
    void setup( @Value("${aws.dynamodb_table}") String table,
                @Value("${aws.dynamodb_table_history}") String tableHistory) {

        testDao = new TestDao( dynamoDbEnhancedAsyncClient, table, tableHistory);
    }

    @Test
    void createMandate() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.createMandate(mandateToInsert).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNotNull( elementFromDb);
            Assertions.assertEquals( mandateToInsert, elementFromDb);
        } catch (Exception e) {
           fail(e);
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void createMandateAlreadyExists() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_2");

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert1.getSk());
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.createMandate(mandateToInsert).block(d);
        Mono<MandateEntity> mono = mandateDao.createMandate(mandateToInsert1);
        assertThrows(PnMandateAlreadyExistsException.class, () -> mono.block(d));


        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateEntity elementFromDb1 = testDao.get(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());

            Assertions.assertNotNull( elementFromDb);
            Assertions.assertNull( elementFromDb1);
            Assertions.assertEquals( mandateToInsert, elementFromDb);
        } catch (Exception e) {
            fail(e);
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void createMandateAlreadyExistsForPG() {
        //Given
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegatorisperson(false);
        mandateToInsert1.setDelegate(mandateToInsert1.getDelegator());
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setDelegatorisperson(false);
        mandateToInsert2.setDelegate(mandateToInsert2.getDelegator());
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");

        try {
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.createMandate(mandateToInsert1).block(d);
        mandateDao.createMandate(mandateToInsert2).block(d);

        //Then
        try {
            MandateEntity elementFromDb1 = testDao.get(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            MandateEntity elementFromDb2 = testDao.get(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());

            Assertions.assertNotNull(elementFromDb1);
            Assertions.assertNotNull(elementFromDb2);
            Assertions.assertEquals(mandateToInsert1, elementFromDb1);
            Assertions.assertEquals(mandateToInsert2, elementFromDb2);
        } catch (Exception e) {
            fail(e);
        } finally {
            try {
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    public static MandateEntity newMandate(boolean withValidtoSetted) {
        MandateEntity m = new MandateEntity();
        m.setMandateId("f271e4bf-0d69-4ed6-a39f-4ef2f01f2fd1");
        m.setDelegatorUid("f271e4bf-0d69-4ed6-a39f-4efdelegator");
        m.setDelegator("PF-f271e4bf-0d69-4ed6-a39f-4efdelegator");
        m.setDelegate("PF-f271e4bf-0d69-4ed6-a39f-4ef2delegate");
        m.setDelegatorisperson(true);
        m.setDelegateisperson(true);
        m.setValidfrom(ZonedDateTime.of(LocalDateTime.of(2021, Month.DECEMBER, 14, 0, 0), ZoneId.of("Europe/Rome")).toInstant());
        m.setValidto(withValidtoSetted?Instant.now().plus(Duration.ofDays(5)):null);
        m.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.PENDING));
        m.setValidationcode("12345");
        m.setVisibilityIds(null);
        return m;
    }

    public static MandateEntity newMandateWithGroups(boolean withValidtoSetted) {
        MandateEntity m = new MandateEntity();
        m.setMandateId("f271e4bf-0d69-4ed6-a39f-4ef2f01f2fd1");
        m.setDelegatorUid("f271e4bf-0d69-4ed6-a39f-4efdelegator");
        m.setDelegator("PF-f271e4bf-0d69-4ed6-a39f-4efdelegator");
        m.setDelegate("PF-f271e4bf-0d69-4ed6-a39f-4ef2delegate");
        m.setDelegatorisperson(true);
        m.setDelegateisperson(true);
        m.setGroups(new HashSet<>(List.of("f271e4bf-0d69-4ed6-a39f", "f271e4bf-0d69-4ed6-a50f")));
        m.setValidfrom(ZonedDateTime.of(LocalDateTime.of(2021, Month.DECEMBER, 14, 0, 0), ZoneId.of("Europe/Rome")).toInstant());
        m.setValidto(withValidtoSetted?Instant.now().plus(Duration.ofDays(5)):null);
        m.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        m.setValidationcode("12345");
        m.setVisibilityIds(null);
        return m;
    }


    private MandateSupportEntity newMandateSupport(MandateEntity source) {
        return new MandateSupportEntity(source);
    }

    @Test
    void listMandatesByDelegatePG() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setGroups(Set.of("RECLAMI"));
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setDelegator(mandateToInsert2.getDelegator() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");

        MandateEntity mandateToInsert3 = newMandate(false);
        mandateToInsert3.setDelegator(mandateToInsert3.getDelegator() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setGroups(Set.of("PIPPO"));
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), null, null, CxTypeAuthFleet.PG, List.of("RECLAMI"))
                .collectList()
                .block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.size());
            Assertions.assertFalse(results.contains(mandateToInsert));
            Assertions.assertTrue(results.contains(mandateToInsert1));
            Assertions.assertFalse(results.contains(mandateToInsert2));
            Assertions.assertFalse(results.contains(mandateToInsert3));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegate() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setDelegator(mandateToInsert2.getDelegator() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        MandateEntity mandateToInsert3 = newMandate(false);
        mandateToInsert3.setDelegator(mandateToInsert3.getDelegator() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), null, null, CxTypeAuthFleet.PF,null).collectList().block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(4, results.size());
            Assertions.assertTrue(results.contains(mandateToInsert));
            Assertions.assertTrue(results.contains(mandateToInsert1));
            Assertions.assertTrue(results.contains(mandateToInsert2));
            Assertions.assertTrue(results.contains(mandateToInsert3));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegateWithMandateId() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setDelegator(mandateToInsert2.getDelegator() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        MandateEntity mandateToInsert3 = newMandate(false);
        mandateToInsert3.setDelegator(mandateToInsert3.getDelegator() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), null, mandateToInsert1.getMandateId(), CxTypeAuthFleet.PF, null).collectList().block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.size());
            Assertions.assertTrue(results.contains(mandateToInsert1));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegateWithMandateId2() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        mandateToInsert.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setDelegator(mandateToInsert2.getDelegator() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        mandateToInsert2.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert3 = newMandate(false);
        mandateToInsert3.setDelegator(mandateToInsert3.getDelegator() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert2.getDelegate(), null, mandateToInsert2.getMandateId(), CxTypeAuthFleet.PF, null).collectList().block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.size());
            Assertions.assertTrue(results.contains(mandateToInsert2));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegateWithMandateId3() {
        //Given
        MandateEntity mandateToInsert = newMandate(true);
        mandateToInsert.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert1 = newMandate(true);
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(true);
        mandateToInsert2.setDelegator(mandateToInsert2.getDelegator() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        mandateToInsert2.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert3 = newMandate(true);
        mandateToInsert3.setDelegator(mandateToInsert3.getDelegator() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert2.getDelegate(), null, mandateToInsert2.getMandateId(),CxTypeAuthFleet.PF, null).collectList().block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.size());
            Assertions.assertTrue(results.contains(mandateToInsert2));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegateActiveOnly() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setDelegator(mandateToInsert2.getDelegator() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        MandateEntity mandateToInsert3 = newMandate(false);
        mandateToInsert3.setDelegator(mandateToInsert3.getDelegator() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), null, CxTypeAuthFleet.PF,null).collectList().block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(2, results.size());
            Assertions.assertFalse(results.contains(mandateToInsert));
            Assertions.assertTrue(results.contains(mandateToInsert1));
            Assertions.assertFalse(results.contains(mandateToInsert2));
            Assertions.assertTrue(results.contains(mandateToInsert3));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }


    @Test
    void listMandatesByDelegateActiveOnlyAndNotExpired() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setDelegator(mandateToInsert2.getDelegator() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        MandateEntity mandateToInsert3 = newMandate(false);
        mandateToInsert3.setDelegator(mandateToInsert3.getDelegator() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        mandateToInsert3.setValidto(DateUtils.atStartOfDay(Instant.now()).minusSeconds(1).toInstant());

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), null, CxTypeAuthFleet.PF, null).collectList().block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.size());
            Assertions.assertFalse(results.contains(mandateToInsert));
            Assertions.assertTrue(results.contains(mandateToInsert1));
            Assertions.assertFalse(results.contains(mandateToInsert2));
            Assertions.assertFalse(results.contains(mandateToInsert3));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegator() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegate(mandateToInsert1.getDelegate() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(true);
        mandateToInsert2.setDelegate(mandateToInsert2.getDelegate() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        MandateEntity mandateToInsert3 = newMandate(true);
        mandateToInsert3.setDelegate(mandateToInsert3.getDelegate() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), null, null, null)
                .collectList()
                .block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(4, results.size());
            Assertions.assertTrue(results.contains(mandateToInsert));
            Assertions.assertTrue(results.contains(mandateToInsert1));
            Assertions.assertTrue(results.contains(mandateToInsert2));
            Assertions.assertTrue(results.contains(mandateToInsert3));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegatorWithMandateId() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegate(mandateToInsert1.getDelegate() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(true);
        mandateToInsert2.setDelegate(mandateToInsert2.getDelegate() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        MandateEntity mandateToInsert3 = newMandate(true);
        mandateToInsert3.setDelegate(mandateToInsert3.getDelegate() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), null, mandateToInsert1.getMandateId(), null)
                .collectList()
                .block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.size());
            Assertions.assertTrue(results.contains(mandateToInsert1));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegatorOnlyActive() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegate(mandateToInsert1.getDelegate() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert2 = newMandate(true);
        mandateToInsert2.setDelegate(mandateToInsert2.getDelegate() + "_2");
        mandateToInsert2.setMandateId(mandateToInsert2.getMandateId() + "_2");
        MandateEntity mandateToInsert3 = newMandate(true);
        mandateToInsert3.setDelegate(mandateToInsert3.getDelegate() + "_3");
        mandateToInsert3.setMandateId(mandateToInsert3.getMandateId() + "_3");
        mandateToInsert3.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(d);
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), null, null)
                .collectList()
                .block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals( 2, results.size());
            Assertions.assertFalse(results.contains(mandateToInsert));
            Assertions.assertTrue(results.contains(mandateToInsert1));
            Assertions.assertFalse(results.contains(mandateToInsert2));
            Assertions.assertTrue(results.contains(mandateToInsert3));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
                testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void listMandatesByDelegatorWithType() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        mandateToInsert.setDelegateisperson(true);
        mandateToInsert.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setDelegateisperson(false);
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), null, DelegateType.PG)
                .collectList()
                .block(d);

        //Then
        try {
            Assertions.assertNotNull(results);
            Assertions.assertEquals( 1, results.size());
            Assertions.assertFalse(results.contains(mandateToInsert));
            Assertions.assertTrue(results.contains(mandateToInsert1));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void acceptMandateNoExpiration() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(), null, CxTypeAuthFleet.PF).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), elementFromDb.getState());
            Assertions.assertNotNull(elementFromDb.getAccepted());
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }


    @Test
    void acceptMandateAlreadyAccepted() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
            mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(),null, CxTypeAuthFleet.PF).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(),null, CxTypeAuthFleet.PF).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), elementFromDb.getState());
            Assertions.assertNotNull(elementFromDb.getAccepted());
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }


    @Test
    void acceptMandateWrongState() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        mandateToInsert.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.EXPIRED));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        Mono<MandateEntity> mono = mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(), null, CxTypeAuthFleet.PF);
        assertThrows(PnInternalException.class, () -> mono.block(d));


        //Then
        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }
    }

    @Test
    void acceptMandateInvalidCode() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        String wrongcode = "99999";

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        Mono<MandateEntity> mono = mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), wrongcode, null, CxTypeAuthFleet.PF);
        assertThrows(PnInvalidVerificationCodeException.class, () -> mono.block(d));


        //Then
        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }
    }


    @Test
    void acceptMandateNotFoud() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        String wrongdelegate = mandateToInsert.getDelegate() + "_wrong";
        String code = mandateToInsert.getValidationcode();

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        Mono<MandateEntity> mono = mandateDao.acceptMandate(wrongdelegate, mandateToInsert.getMandateId(), code, null, CxTypeAuthFleet.PF);
        assertThrows(PnMandateNotFoundException.class, () -> mono.block(d));


        //Then
        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }
    }


    @Test
    void acceptMandateExpiration() {
        //Given
        MandateEntity mandateToInsert = newMandate(true);
        MandateSupportEntity mandateSupport = newMandateSupport(mandateToInsert);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(), null,CxTypeAuthFleet.PF).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateSupportEntity elementSupportFromDb = testDao.getSupport(mandateSupport.getDelegator(), mandateSupport.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), elementFromDb.getState());
            Assertions.assertNotNull(elementFromDb.getAccepted());
            Assertions.assertNotNull(elementSupportFromDb);
            Assertions.assertEquals( mandateSupport, elementSupportFromDb);

        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void acceptMandatePgNullGroup() {
        //Given
        MandateEntity mandateToInsert = newMandate(true);
        MandateSupportEntity mandateSupport = newMandateSupport(mandateToInsert);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(), null, CxTypeAuthFleet.PG).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateSupportEntity elementSupportFromDb = testDao.getSupport(mandateSupport.getDelegator(), mandateSupport.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertEquals(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), elementFromDb.getState());
            Assertions.assertNotNull(elementFromDb.getAccepted());
            Assertions.assertNotNull(elementSupportFromDb);
            Assertions.assertEquals(mandateSupport, elementSupportFromDb);

        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void acceptMandatePgEmptyGroup() {
        //Given
        MandateEntity mandateToInsert = newMandate(true);
        MandateSupportEntity mandateSupport = newMandateSupport(mandateToInsert);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(), Collections.emptyList(), CxTypeAuthFleet.PG).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateSupportEntity elementSupportFromDb = testDao.getSupport(mandateSupport.getDelegator(), mandateSupport.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertEquals(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), elementFromDb.getState());
            Assertions.assertNotNull(elementFromDb.getAccepted());
            Assertions.assertNotNull(elementSupportFromDb);
            Assertions.assertEquals(mandateSupport, elementSupportFromDb);

        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void acceptMandatePgGroup() {
        //Given
        MandateEntity mandateToInsert = newMandate(true);
        MandateSupportEntity mandateSupport = newMandateSupport(mandateToInsert);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode(), List.of("G1"), CxTypeAuthFleet.PG).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateSupportEntity elementSupportFromDb = testDao.getSupport(mandateSupport.getDelegator(), mandateSupport.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertEquals(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), elementFromDb.getState());
            Assertions.assertNotNull(elementFromDb.getAccepted());
            Assertions.assertNotNull(elementSupportFromDb);
            Assertions.assertEquals(mandateSupport, elementSupportFromDb);
            Assertions.assertTrue(elementFromDb.getGroups().contains("G1"));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void updateMandate(){
        //Given
        MandateEntity mandateToInsert = newMandateWithGroups(true);
        MandateSupportEntity mandateSupport = newMandateSupport(mandateToInsert);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.updateMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getGroups()).block(d);

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateSupportEntity elementSupportFromDb = testDao.getSupport(mandateSupport.getDelegator(), mandateSupport.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertNotNull(elementFromDb.getAccepted());
            Assertions.assertNotNull(elementSupportFromDb);
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void rejectMandate() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.rejectMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId()).block(d);


        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateEntity elementHistoryFromDb = testDao.getHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNull( elementFromDb);
            Assertions.assertNotNull( elementHistoryFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.REJECTED), elementHistoryFromDb.getState());
            Assertions.assertNotNull(elementHistoryFromDb.getRejected());
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }


    @Test
    void rejectMandateActive() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        mandateToInsert.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.rejectMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId()).block(d);


        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateEntity elementHistoryFromDb = testDao.getHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNull( elementFromDb);
            Assertions.assertNotNull( elementHistoryFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.REJECTED), elementHistoryFromDb.getState());
            Assertions.assertNotNull(elementHistoryFromDb.getRejected());
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }


    @Test
    void rejectMandateNotFound() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        String wrongdelegate = mandateToInsert.getDelegate() + "_WRONG";

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        Mono<MandateEntity> mono =  mandateDao.rejectMandate(wrongdelegate, mandateToInsert.getMandateId());
        assertThrows(PnMandateNotFoundException.class, () -> mono.block(d));


        //Then
        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.deleteHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }
    }

    @Test
    void revokeMandate() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.revokeMandate(mandateToInsert.getDelegator(), mandateToInsert.getMandateId()).block(d);


        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateEntity elementHistoryFromDb = testDao.getHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNull( elementFromDb);
            Assertions.assertNotNull( elementHistoryFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.REVOKED), elementHistoryFromDb.getState());
            Assertions.assertNotNull(elementHistoryFromDb.getRevoked());
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }


    @Test
    void revokeMandateTwice() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }
        mandateDao.revokeMandate(mandateToInsert.getDelegator(), mandateToInsert.getMandateId()).block(d);


        //When
        mandateDao.revokeMandate(mandateToInsert.getDelegator(), mandateToInsert.getMandateId()).block(d);


        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateEntity elementHistoryFromDb = testDao.getHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNull( elementFromDb);
            Assertions.assertNotNull( elementHistoryFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.REVOKED), elementHistoryFromDb.getState());
            Assertions.assertNotNull(elementHistoryFromDb.getRevoked());
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void expireMandate() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.expireMandate(mandateToInsert.getDelegator(), mandateToInsert.getDelegatorUid(), mandateToInsert.getDelegatorisperson()?"PF":"PG", mandateToInsert.getMandateId()).block(d);


        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            MandateEntity elementHistoryFromDb = testDao.getHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNull( elementFromDb);
            Assertions.assertNotNull( elementHistoryFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.EXPIRED), elementHistoryFromDb.getState());
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
                testDao.deleteHistory(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }


    @Test
    void expireMandateNotFound() {
        //Given
        // nothing

        //When
        Mono<MandateEntity> mono = mandateDao.expireMandate("fake", "fake", "PF", "fake");
        assertThrows(PnMandateNotFoundException.class, () -> mono.block(d));
    }

    @Test
    void listMandatesByDelegators() {
        MandateEntity mandateToInsert = newMandate(true);
        mandateToInsert.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        MandateByDelegatorRequestDto requestDto = new MandateByDelegatorRequestDto();
        requestDto.setDelegatorId(mandateToInsert.getDelegator());
        requestDto.setMandateId(mandateToInsert.getMandateId());

        try {
            List<MandateEntity> result = mandateDao.listMandatesByDelegators(List.of(requestDto))
                    .collectList()
                    .block(d);

            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals(mandateToInsert, result.get(0));
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            try {
                testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void searchByDelegate1() {
        MandateEntity mandateToInsert1 = newMandate(false);
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setMandateId(mandateToInsert1.getMandateId() + "-2");
        mandateToInsert2.setDelegator(mandateToInsert1.getDelegator() + "-2");
        try {
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            mandateDao.createMandate(mandateToInsert2).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        String delegateId = mandateToInsert1.getDelegate();
        try {
            Page<MandateEntity> result = mandateDao.searchByDelegate(delegateId, null, null, null, 1, null)
                    .block(d);
            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.items().size());
            Assertions.assertNotNull(result.lastEvaluatedKey());
        } finally {
            try {
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void searchByDelegate2() {
        MandateEntity mandateToInsert1 = newMandate(false);
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        mandateToInsert1.setGroups(Set.of("G1"));
        MandateEntity mandateToInsert2 = newMandate(false);
        try {
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            mandateDao.createMandate(mandateToInsert2).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        String delegateId = mandateToInsert1.getDelegate();
        String mandateId = mandateToInsert1.getMandateId();
        Integer state = mandateToInsert1.getState();
        try {
            Page<MandateEntity> result = mandateDao.searchByDelegate(delegateId, state, List.of("G1", "G2"), List.of("PF-f271e4bf-0d69-4ed6-a39f-4efdelegator","other"), 2, null)
                    .block(d);
            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.items().size());
            Assertions.assertEquals(mandateId, result.items().get(0).getMandateId());
            Assertions.assertNull(result.lastEvaluatedKey());
        } finally {
            try {
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }

    @Test
    void searchByDelegate3() {
        MandateEntity mandateToInsert1 = newMandate(false);
        MandateEntity mandateToInsert2 = newMandate(false);
        mandateToInsert2.setMandateId(mandateToInsert1.getMandateId() + "-2");
        mandateToInsert2.setDelegator(mandateToInsert1.getDelegator() + "-2");
        try {
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert1).block(d);
            mandateDao.createMandate(mandateToInsert2).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        String delegateId = mandateToInsert1.getDelegate();
        try {
            Page<MandateEntity> result1 = mandateDao.searchByDelegate(delegateId, null, null, null, 1, null)
                    .block(d);
            Assertions.assertNotNull(result1);
            Assertions.assertNotNull(result1.lastEvaluatedKey());
            Assertions.assertEquals(1, result1.items().size());

            PnLastEvaluatedKey lastEvaluatedKey = new PnLastEvaluatedKey();
            lastEvaluatedKey.setInternalLastEvaluatedKey(result1.lastEvaluatedKey());
            Page<MandateEntity> result2 = mandateDao.searchByDelegate(delegateId, null, null, null, 2, lastEvaluatedKey)
                    .block(d);
            Assertions.assertNotNull(result2);
            Assertions.assertEquals(1, result2.items().size());
            Assertions.assertNull(result2.lastEvaluatedKey());
        } finally {
            try {
                testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
                testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            } catch (Exception e) {
                System.out.println("Nothing to remove");
            }
        }
    }
}