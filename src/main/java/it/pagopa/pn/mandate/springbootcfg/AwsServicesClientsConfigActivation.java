package it.pagopa.pn.mandate.springbootcfg;

import it.pagopa.pn.commons.configs.RuntimeMode;
import it.pagopa.pn.commons.configs.aws.AwsConfigs;
import it.pagopa.pn.commons.configs.aws.AwsServicesClientsConfig;
import it.pagopa.pn.mandate.utils.DynamoDbAsyncClientDecorator;
import it.pagopa.pn.mandate.utils.DynamoDbEnhancedAsyncClientDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

@Configuration
public class AwsServicesClientsConfigActivation extends AwsServicesClientsConfig {

    public AwsServicesClientsConfigActivation(AwsConfigs props) {
        super(props, RuntimeMode.PROD);
    }

    @Bean
    @Primary
    DynamoDbAsyncClient dynamoDbAsyncClientDecorator(DynamoDbAsyncClient delegate) {
        return new DynamoDbAsyncClientDecorator(delegate);
    }

    @Bean
    @Primary
    DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClientDecorator(DynamoDbEnhancedAsyncClient delegate) {
        return new DynamoDbEnhancedAsyncClientDecorator(delegate);
    }

}
