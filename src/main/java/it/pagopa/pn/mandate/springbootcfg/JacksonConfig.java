package it.pagopa.pn.mandate.springbootcfg;

import it.pagopa.pn.ciechecker.utils.TestMixIn;
import it.pagopa.pn.common.rest.error.v1.dto.Problem;
import it.pagopa.pn.common.rest.error.v1.dto.ProblemError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        Jackson2ObjectMapperBuilder b = new Jackson2ObjectMapperBuilder();
        // Associa il Mix-In alla classe generata dal plugin
        b.mixIn(Problem.class, TestMixIn.class);
        return b;
    }
}