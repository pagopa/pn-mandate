package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import org.junit.Assert;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

@SpringBootTest(properties = {"aws.dynamodb_table=IT-Mandate","aws.dynamodb_table_history=IT-MandateHistory"})
public class BaseIT {

    private static final String TABLE_NAME = "IT-Mandate";
    private static final String TABLE_NAME_HISTORY = "IT-MandateHistory";

    static LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.0.4.1.nodejs18"))
                    .withServices(LocalStackContainer.Service.DYNAMODB);

    public static DynamoDbClient dynamoDbClient;

    static {
        localstack.start();

        dynamoDbClient = DynamoDbClient
                .builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                        )
                )
                .region(Region.of(localstack.getRegion()))
                .build();
        createTestTables();
    }

    @DynamicPropertySource
    static void overrideConfiguration(DynamicPropertyRegistry registry) {
        //registry.add("event-processing.order-event-queue", () -> QUEUE_NAME);
        //registry.add("event-processing.order-event-bucket", () -> BUCKET_NAME);
        registry.add("aws.endpoint-url", () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB));
        registry.add("aws.credentials.access-key", localstack::getAccessKey);
        registry.add("aws.credentials.secret-key", localstack::getSecretKey);
    }



    static void createTestTables() {
        CreateTableResponse res = createMandateTable(TABLE_NAME);
        CreateTableResponse res1 = createMandateTable(TABLE_NAME_HISTORY);

        ListTablesResponse tables = dynamoDbClient.listTables();
        Assert.assertEquals(1, tables.tableNames().stream().filter(x -> x.equals(TABLE_NAME)).count());
        Assert.assertEquals(1, tables.tableNames().stream().filter(x -> x.equals(TABLE_NAME_HISTORY)).count());
    }

    private static CreateTableResponse createMandateTable(String tableName){

        List<AttributeDefinition> attributeDefinitions =
                List.of(AttributeDefinition.builder()
                                .attributeName(MandateEntity.COL_PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(MandateEntity.COL_SK)
                                .attributeType(ScalarAttributeType.S)
                                .build());
        if (tableName.equals(TABLE_NAME))
        {
            attributeDefinitions = List.of(attributeDefinitions.get(0),
                    attributeDefinitions.get(1),
                    AttributeDefinition.builder()
                            .attributeName(MandateEntity.COL_S_DELEGATE)
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                    AttributeDefinition.builder()
                            .attributeName(MandateEntity.COL_I_STATE)
                            .attributeType(ScalarAttributeType.N)
                            .build());
        }

        List<KeySchemaElement> ks =
                List.of(KeySchemaElement.builder()
                                .attributeName(MandateEntity.COL_PK)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(MandateEntity.COL_SK)
                                .keyType(KeyType.RANGE)
                                .build());

        ProvisionedThroughput provisionedthroughput = ProvisionedThroughput.builder()
                .readCapacityUnits(5L)
                .writeCapacityUnits(5L)
                .build();

        List<GlobalSecondaryIndex> globalSecondaryIndexes = null;
        if (tableName.equals(TABLE_NAME)) {
            globalSecondaryIndexes =
                    List.of(GlobalSecondaryIndex.builder()
                            .indexName(BaseDao.GSI_INDEX_DELEGATE_STATE)
                            .keySchema(List.of(KeySchemaElement.builder()
                                            .attributeName(MandateEntity.COL_S_DELEGATE)
                                            .keyType(KeyType.HASH)
                                            .build(),
                                    KeySchemaElement.builder()
                                            .attributeName(MandateEntity.COL_I_STATE)
                                            .keyType(KeyType.RANGE)
                                            .build()))
                            .projection(Projection.builder().projectionType("ALL").build())
                            .provisionedThroughput(provisionedthroughput)
                            .build());
        }

        return createDynamoTable(tableName, attributeDefinitions, ks, provisionedthroughput, globalSecondaryIndexes);
    }

    private static CreateTableResponse createDynamoTable(String tableName,
                                                         List<AttributeDefinition> attributeDefinitions, List<KeySchemaElement> ks,
                                                         ProvisionedThroughput provisionedthroughput, List<GlobalSecondaryIndex> globalSecondaryIndexes) {

        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(attributeDefinitions)
                .keySchema(ks)
                .provisionedThroughput(provisionedthroughput)
                .globalSecondaryIndexes(globalSecondaryIndexes)
                .build();

        return dynamoDbClient.createTable(request);
    }

}
