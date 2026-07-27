package com.mkalki.cloudtaskapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("CloudTask API")
                        .version("1.0.0")
                        .description("A REST API for task management built with Spring Boot. " +
                                "Supports CRUD operations, pagination, sorting, filtering, search, due dates, priorities, and soft delete.")
                        .contact(new Contact().name("mkalki")
                                .email("mkalki.08@gmail.com")
                                .url("https://github.com/mkalki"))
                );
    }
}
