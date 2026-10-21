package com.timedeal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TimedealApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimedealApplication.class, args);
    }
}
