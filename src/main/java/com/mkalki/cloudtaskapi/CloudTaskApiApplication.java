package com.mkalki.cloudtaskapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class CloudTaskApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudTaskApiApplication.class, args);
    }

}
