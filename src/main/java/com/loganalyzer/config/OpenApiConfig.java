package com.loganalyzer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI logAnalyzerOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("LogSpectra Backend API")
                .version("v1")
                .description("API documentation for log ingestion, search, AI analysis, issue grouping, and AI settings.")
                .license(new License().name("Internal Use")));
    }
}
