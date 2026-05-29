package com.example.athletehub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;

/**
 * Shared web beans. CORS is configured in {@code security/SecurityConfig}
 * (Spring Security owns the filter chain). This holds a timeout-bounded
 * {@link RestTemplate} for any future outbound HTTP calls, and a system
 * {@link Clock} so time-sensitive services ({@code TrainingService} for
 * "today's plan") can be unit/integration-tested with a fixed clock.
 */
@Configuration
public class WebConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
