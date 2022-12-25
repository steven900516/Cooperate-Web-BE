package com.conghuhu;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author conghuhu
 * @create 2021-09-24 19:51
 */
@SpringBootApplication
@EnableAdminServer

public class SpringBootApplicationMain {
    public static void main(String[] args) {
        SpringApplication.run(SpringBootApplicationMain.class, args);
    }


    @Bean
    MeterRegistryCustomizer<MeterRegistry> configurer(
            @Value("${spring.application.name}") String applicationName) {
        return (registry) -> registry.config().commonTags("application", applicationName);
    }
}
