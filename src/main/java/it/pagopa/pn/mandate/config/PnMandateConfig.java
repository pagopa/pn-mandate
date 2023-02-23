package it.pagopa.pn.mandate.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pn.mandate")
@Import(SharedAutoConfiguration.class)
public class PnMandateConfig {

    private String clientInfopaBasepath;
    private String clientDatavaultBasepath;

    @Value("${aws.dynamodb_table:}")
    private String dynamodbTable;
    @Value("${aws.dynamodb_table_history:}")
    private String dynamodbTableHistory;

    private Integer maxPageSize;
}
