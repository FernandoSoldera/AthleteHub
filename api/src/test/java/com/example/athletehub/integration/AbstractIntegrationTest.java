package com.example.athletehub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for backend integration tests (*IT, run by Failsafe in `verify`).
 *
 * <p>Boots the full Spring context on a random port against a real PostgreSQL
 * (Testcontainers) so Flyway migrations run exactly as in production. Also
 * starts an in-process SMTP server (GreenMail) so tests can read activation /
 * reset codes from real emails the app sends.
 *
 * <p>Subclasses get a {@link RestTemplate} rooted at the running port that
 * <em>never</em> throws on 4xx/5xx — negative-path tests assert on status. The
 * HTTP client is {@link JdkClientHttpRequestFactory} (Java's
 * {@code java.net.http.HttpClient}), <em>not</em> the older
 * {@code SimpleClientHttpRequestFactory} — that one wraps {@code HttpURLConnection},
 * whose HTTP-auth state machine silently consumes the body of 401 responses
 * (real clients like Flutter's {@code http} package don't have that quirk).
 *
 * <p>Requires Docker to be running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("athletehub_it")
                    .withUsername("it_user")
                    .withPassword("it_password");

    /** In-process SMTP. Fixed port (ServerSetupTest.SMTP = 3025), auth disabled. */
    protected static final GreenMail GREEN_MAIL =
            new GreenMail(ServerSetupTest.SMTP)
                    .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    static {
        POSTGRES.start();
        GREEN_MAIL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    }

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    /** RestTemplate rooted at the running server; never throws on 4xx/5xx so
     *  tests can assert on status. */
    protected RestTemplate rest;

    @BeforeEach
    void setUpRestAndResetMail() {
        rest = new RestTemplate(new JdkClientHttpRequestFactory());
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + port));
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        // Clear any emails left over from the previous test so getReceivedMessages() is deterministic.
        GREEN_MAIL.reset();
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
