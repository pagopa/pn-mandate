package it.pagopa.pn.mandate;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@Slf4j
public class ApplicationConfiguration  {

    @Value("${spring.application.name}")
    private String springApplicationName;

    @PostConstruct
    public void init(){
        System.setProperty("spring.application.name", springApplicationName);
    }
}