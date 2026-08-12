package com.apitracker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JiraProperties.class, MailProperties.class})
public class IntegrationConfig {
}
