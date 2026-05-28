package com.example.athletehub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Base class for backend integration tests (*IT, run by Failsafe in `verify`).
 *
 * <p>Boots the full Spring context on a random port against a real PostgreSQL
 * (Testcontainers) so Flyway migrations run exactly as in production. Subclasses
 * get a {@link RestTemplate} rooted at the running port that <em>never</em>
 * follows redirects and <em>never</em> throws on 4xx/5xx — negative-path tests
 * assert on the returned status instead.
 *
 * <p>Requires Docker to be running. Mirrors lotuga's pattern; as features land,
 * extend this base with GreenMail / WireMock / auth helpers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("athletehub_it")
                    .withUsername("it_user")
                    .withPassword("it_password");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    /** RestTemplate rooted at the running server; never throws on 4xx/5xx so
     *  tests can assert on status. Also does not follow redirects (otherwise an
     *  unauthenticated request to a protected endpoint could land on a 200 page
     *  and mask the actual 401/302). */
    protected RestTemplate rest;

    @BeforeEach
    void setUpRest() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        rest = new RestTemplate(factory);
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + port));
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false; // surface the status to the test instead of throwing
            }
        });
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + raw, e);
        }
    }
}
