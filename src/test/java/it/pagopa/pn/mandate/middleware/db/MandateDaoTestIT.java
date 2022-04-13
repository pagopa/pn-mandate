package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateSupportEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.utils.InvalidVerificationCodeException;
import it.pagopa.pn.mandate.rest.utils.MandateAlreadyExistsException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;


@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
        "aws.region-code=us-east-1",
        "aws.profile-name=${PN_AWS_PROFILE_NAME:default}",
        "aws.endpoint-url=http://localhost:4566"
})
@SpringBootTest
public class MandateDaoTestIT {

    @Autowired
    private MandateDao mandateDao;

    @Autowired
    DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient;

    TestDao testDao;

    @BeforeEach
    void setup( @Value("${aws.dynamodb.table}") String table,
                @Value("${aws.dynamodb.table_history}") String tableHistory) {
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
        mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));

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
        mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        try {
            mandateDao.createMandate(mandateToInsert1).block(Duration.ofMillis(3000));
            fail("no MandateAlreadyExistsException thrown");
        } catch (MandateAlreadyExistsException e) {
            // expected
        }


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
        m.setValidfrom("2021-12-14+01:00");
        m.setValidto(withValidtoSetted?"2023-01-01":null);
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
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), null).collectList().block(Duration.ofMillis(3000));

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
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegate(mandateToInsert.getDelegate(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE)).collectList().block(Duration.ofMillis(3000));

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
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), null).collectList().block(Duration.ofMillis(3000));

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
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert2.getDelegator(), mandateToInsert2.getSk());
            mandateDao.createMandate(mandateToInsert2).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert3.getDelegator(), mandateToInsert3.getSk());
            mandateDao.createMandate(mandateToInsert3).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        List<MandateEntity> results = mandateDao.listMandatesByDelegator(mandateToInsert.getDelegator(), StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE)).collectList().block(Duration.ofMillis(3000));

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
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode()).block(Duration.ofMillis(3000));

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
    void acceptMandateInvalidCode() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);
        String wrongcode = "99999";

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        try {
            mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), wrongcode).block(Duration.ofMillis(3000));
            fail("no invalid code exception");
        } catch (InvalidVerificationCodeException e) {
           // expected
        }

        //Then
        try {
            MandateEntity elementFromDb = testDao.get(mandateToInsert.getDelegator(), mandateToInsert.getSk());

            Assertions.assertNotNull(elementFromDb);
            Assertions.assertEquals( StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.PENDING), elementFromDb.getState());
            Assertions.assertNull(elementFromDb.getAccepted());
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
    void acceptMandateExpiration() {
        //Given
        MandateEntity mandateToInsert = newMandate(true);
        MandateSupportEntity mandateSupport = newMandateSupport(mandateToInsert);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            testDao.deleteSupport(mandateSupport.getDelegator(), mandateSupport.getSk());
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.acceptMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId(), mandateToInsert.getValidationcode()).block(Duration.ofMillis(3000));

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
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.rejectMandate(mandateToInsert.getDelegate(), mandateToInsert.getMandateId()).block(Duration.ofMillis(3000));


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
    void revokeMandate() {
        //Given
        MandateEntity mandateToInsert = newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.revokeMandate(mandateToInsert.getDelegator(), mandateToInsert.getMandateId()).block(Duration.ofMillis(3000));


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
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        mandateDao.expireMandate(mandateToInsert.getDelegator(), mandateToInsert.getMandateId()).block(Duration.ofMillis(3000));


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
}