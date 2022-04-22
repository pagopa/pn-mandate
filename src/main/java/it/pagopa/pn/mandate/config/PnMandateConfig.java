package it.pagopa.pn.mandate.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pn.mandate")
public class PnMandateConfig {

    private String clientInfopaBasepath;
    private String clientDatavaultBasepath;
}
