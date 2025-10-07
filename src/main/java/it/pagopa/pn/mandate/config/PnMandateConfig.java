package it.pagopa.pn.mandate.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pn.mandate")
@Import(SharedAutoConfiguration.class)
@EnableScheduling
public class PnMandateConfig {

    private String clientExtregBasepath;
    private String clientDatavaultBasepath;
    private String clientDeliveryBasepath;

    @Value("${aws.dynamodb_table:}")
    private String dynamodbTable;
    @Value("${aws.dynamodb_table_history:}")
    private String dynamodbTableHistory;

    private Integer maxPageSize;

    private Duration pendingDuration;
    private Duration ciePendingDuration;

    //@Value("${ciechecker.cscaAnchor.pathFileName}")
    private String ciecheckerCscaAnchorPathFilename;

    @Value("${aws.profile-name}")
    private String profileName ;



    public <B extends AwsClientBuilder> B configureBuilder(B builder) {
        String profileName = this.getProfileName();
        log.info("PROFILE NAME: {}" , profileName);
        if( StringUtils.isNotBlank( profileName ) ) {
            builder.credentialsProvider( ProfileCredentialsProvider.create( profileName ));
        }

        String regionCode = Region.EU_SOUTH_1.toString();
        if( StringUtils.isNotBlank( regionCode )) {
            builder.region( Region.of( regionCode ));
        }

        return builder;
    }

}
