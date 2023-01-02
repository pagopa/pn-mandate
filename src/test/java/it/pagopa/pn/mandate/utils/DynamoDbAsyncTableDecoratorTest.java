package it.pagopa.pn.mandate.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.enhanced.dynamodb.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DynamoDbAsyncTableDecoratorTest {

    private DynamoDbAsyncTableDecorator<String> dynamoDbAsyncTableDecorator;

    private DynamoDbAsyncTable<String> delegate;

    @BeforeEach
    public void init() {
        delegate = Mockito.mock(DynamoDbAsyncTable.class);
        dynamoDbAsyncTableDecorator = new DynamoDbAsyncTableDecorator<>(delegate);
    }

    @Test
    void mapperExtensionTest() {
        DynamoDbEnhancedClientExtension expectedValue = delegate.mapperExtension();
        Mockito.when(delegate.mapperExtension()).thenReturn(expectedValue);
        assertThat(dynamoDbAsyncTableDecorator.mapperExtension()).isEqualTo(expectedValue);
    }

    @Test
    void tableSchemaTest() {
        TableSchema expectedValue = delegate.tableSchema();
        Mockito.when(delegate.tableSchema()).thenReturn(expectedValue);
        assertThat(dynamoDbAsyncTableDecorator.tableSchema()).isEqualTo(expectedValue);
    }

    @Test
    void indexTest() {
        DynamoDbAsyncIndex<String> index = Mockito.mock(DynamoDbAsyncIndex.class);
        Mockito.when(delegate.index("INDEX")).thenReturn(index);
        assertThat(dynamoDbAsyncTableDecorator.index("INDEX")).isEqualTo(new DynamoDbAsyncIndexDecorator(index));
    }

    @Test
    void tableNameTest() {
        Mockito.when(delegate.tableName()).thenReturn("MANDATE");
        assertThat(dynamoDbAsyncTableDecorator.tableName()).isEqualTo("MANDATE");
    }

    @Test
    void keyFromTest() {
        Key key = delegate.keyFrom("A");
        Mockito.when(delegate.keyFrom("A")).thenReturn(key);
        assertThat(dynamoDbAsyncTableDecorator.keyFrom("A")).isEqualTo(key);
    }

}
