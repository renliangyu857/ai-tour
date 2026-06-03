package com.tourism.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Agent 层所需的额外 Bean 配置。
 * ObjectMapper 由 Spring Boot 自动配置，此处只补充 RestTemplate。
 */
@Configuration
public class AgentConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
