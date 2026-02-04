package it.pagopa.pn.mandate.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

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
    private Duration cieValidToDuration;

    private Topics topics;

    private Boolean revokeCieMandateOnVerificationFailure;

    private String ciecheckerCscaAnchorPathFilename;

    @Value("${aws.region-code}")
    private String regionCode;

    private String generatorBucketName;
    private String generatorZipName;

    private static final DefaultCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER_V2 = DefaultCredentialsProvider.create();

    @Data
    public static class Topics {
        private String mandateInputs;
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder clientBuilder = configureBuilder(S3Client.builder());
        return clientBuilder.build();
    }

    public <B extends AwsClientBuilder<?, ?>> B configureBuilder(B builder) {
        log.info("Using DefaultCredentialsProvider for AWS credentials");
        builder.credentialsProvider(DEFAULT_CREDENTIALS_PROVIDER_V2);

        // verifichiamo se aws.region-code è valorizzata nel caso la impostiamo
        if(this.regionCode != null) {
            log.info("AWS regionCode configurata: {}", regionCode);
            builder.region(Region.of(this.regionCode));
        } else {
            log.warn("AWS regionCode non è valorizzata, viene usato il default");
        }
        return builder;
    }



}
