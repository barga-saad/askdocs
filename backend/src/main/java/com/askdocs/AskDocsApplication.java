package com.askdocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AskDocsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AskDocsApplication.class, args);
    }
}
