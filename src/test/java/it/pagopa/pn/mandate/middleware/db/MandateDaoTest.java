package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.PnAuditLogBuilder;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.exceptions.PnInvalidVerificationCodeException;
import it.pagopa.pn.mandate.exceptions.PnMandateAlreadyExistsException;
import it.pagopa.pn.mandate.exceptions.PnMandateNotFoundException;
import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateSupportEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
public class MandateDaoTest extends BaseIT {

    private final Duration d = Duration.ofMillis(60000);

    private MandateDao mandateDao;

    @Autowired
    PnMandateConfig cfg;

    @Autowired
    PnAuditLogBuilder pnAuditLogBuilder;

    TestDao testDao;



    @BeforeEach
    void setup( @Value("${aws.dynamodb_table}") String table,
                @Value("${aws.dynamodb_table_history}") String tableHistory) {
        DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient = dynamoDbEnhancedAsyncClient(dynamoDbAsyncClient());
        testDao = new TestDao( dynamoDbEnhancedAsyncClient, table, tableHistory);
        this.mandateDao = new MandateDao(dynamoDbEnhancedAsyncClient,
                dynamoDbAsyncClient(),
                cfg,
                pnAuditLogBuilder);
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


    public static MandateEntity newMandate(boolean withValidtoSetted) {
        MandateEntity m = new MandateEntity();
        m.setMandateId("f271e4bf-0d69-4ed6-a39f-4ef2f01f2fd1");
        m.setDelegator("f271e4bf-0d69-4ed6-a39f-4efdelegator");
        m.setDelegate("f271e4bf-0d69-4ed6-a39f-4ef2delegate");
        m.setDelegatorisperson(true);
        m.setDelegateisperson(true);
        m.setValidfrom(ZonedDateTime.of(LocalDateTime.of(2021, Month.DECEMBER, 14, 0, 0), ZoneId.of("Europe/Rome")).toInstant());
        m.setValidto(withValidtoSetted?Instant.now().plus(Duration.ofDays(5)):null);
        m.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.PENDING));
        m.setValidationcode("12345");
        m.setVisibilityIds(null);
        return m;
    }


    private MandateSupportEntity newMandateSupport(MandateEntity source) {
        return new MandateSupportEntity(source);
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
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), null, null).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), null, mandateToInsert1.getMandateId()).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert2.getDelegate(), null, mandateToInsert2.getMandateId()).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert2.getDelegate(), null, mandateToInsert2.getMandateId()).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), null).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), null).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), null, null).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), null, mandateToInsert1.getMandateId()).collectList().block(d);

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
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE), null).collectList().block(d);

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
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode()).block(d);

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
            mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode()).block(d);
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode()).block(d);

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
        Mono<Void> mono = mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode());
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
        Mono<Void> mono = mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), wrongcode);
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
        Mono<Void> mono = mandateDao.acceptMandate(wrongdelegate, mandateToInsert.getMandateId(), code);
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
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode()).block(d);

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
        Mono<Void> mono =  mandateDao.rejectMandate(wrongdelegate, mandateToInsert.getMandateId());
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
        mandateDao.expireMandate(mandateToInsert.getDelegator(), mandateToInsert.getMandateId()).block(d);


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
        Mono<Object> mono = mandateDao.expireMandate("fake", "fake");
        assertThrows(PnMandateNotFoundException.class, () -> mono.block(d));
    }
}