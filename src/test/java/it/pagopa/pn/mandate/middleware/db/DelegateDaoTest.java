package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.commons.log.PnAuditLogBuilder;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.DelegateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(SpringExtension.class)
class DelegateDaoTest extends BaseIT {

    private MandateDao mandateDao;

    private DelegateDao delegateDao;

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
        this.delegateDao = new DelegateDao(dynamoDbEnhancedAsyncClient, dynamoDbAsyncClient(), cfg);
        this.mandateDao = new MandateDao(dynamoDbEnhancedAsyncClient, dynamoDbAsyncClient(), cfg, pnAuditLogBuilder);
    }

    @Test
    void countMandatesPendingOnly() {
        //Given
        MandateEntity mandateToInsert = MandateDaoTest.newMandate(false);

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        DelegateEntity result = delegateDao.countMandates(mandateToInsert.getDelegate()).block(Duration.ofMillis(3000));

        //Then
        try {
            Assertions.assertNotNull( result);
            Assertions.assertEquals(1,  result.getPendingcount());
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
    void countMandatesPendingAndActive() {
        //Given
        MandateEntity mandateToInsert = MandateDaoTest.newMandate(false);
        MandateEntity mandateToInsert1 = MandateDaoTest.newMandate(false);
        mandateToInsert1.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));
        mandateToInsert1.setDelegator(mandateToInsert1.getDelegator() + "_1");
        mandateToInsert1.setMandateId(mandateToInsert1.getMandateId() + "_1");

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
            testDao.delete(mandateToInsert1.getDelegator(), mandateToInsert1.getSk());
            mandateDao.createMandate(mandateToInsert1).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        DelegateEntity result = delegateDao.countMandates(mandateToInsert.getDelegate()).block(Duration.ofMillis(3000));

        //Then
        try {
            Assertions.assertNotNull( result);
            Assertions.assertEquals(1,  result.getPendingcount());
        } catch (Exception e) {
            fail(e);
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
    void countMandatesActiveOnly() {
        //Given
        MandateEntity mandateToInsert = MandateDaoTest.newMandate(false);
        mandateToInsert.setState(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.ACTIVE));

        try {
            testDao.delete(mandateToInsert.getDelegator(), mandateToInsert.getSk());
            mandateDao.createMandate(mandateToInsert).block(Duration.ofMillis(3000));
        } catch (Exception e) {
            System.out.println("Nothing to remove");
        }

        //When
        DelegateEntity result = delegateDao.countMandates(mandateToInsert.getDelegate()).block(Duration.ofMillis(3000));

        //Then
        try {
            Assertions.assertNotNull( result);
            Assertions.assertEquals(0,  result.getPendingcount());
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
}