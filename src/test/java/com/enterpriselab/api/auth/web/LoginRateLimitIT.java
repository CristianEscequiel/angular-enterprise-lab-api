package com.enterpriselab.api.auth.web;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.auth.domain.LoginAttemptLimiter;
import com.enterpriselab.api.auth.domain.MutableClock;
import com.enterpriselab.api.auth.domain.TooManyAttemptsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 06, tarea 5b: el límite de intentos de {@code POST /auth/login} de punta a punta (cadena de
 * seguridad completa, Postgres real, usuarios del seed). Umbrales reales 5 por usuario y 20 por IP,
 * ventana de 15 minutos, con un reloj que solo avanza cuando el test lo pide.
 */
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.login-rate-limit.max-attempts-per-user=5",
        "app.security.login-rate-limit.max-attempts-per-ip=20",
        "app.security.login-rate-limit.window=15m"})
class LoginRateLimitIT extends AbstractPostgresIT {

    private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");
    private static final String SECRET_MARKER = "Cl4ve-Que-No-Debe-Loguearse-9";

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MutableClock clock;
    @Autowired
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void resetState() {
        limiter.reset();
        clock.set(START);
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MvcResult login(String username, String password, String ip) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/auth/login").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void failLogins(String username, int times, String ip) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(login(username, "incorrecta", ip).getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void req1_1_theFifthFailedAttemptIs401() throws Exception {
        failLogins("admin", 4, "10.0.0.1");

        assertThat(login("admin", "incorrecta", "10.0.0.1").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void req1_1b_theSixthAttemptIs429EvenWithTheCorrectPassword() throws Exception {
        failLogins("admin", 5, "10.0.0.1");

        MvcResult blocked = login("admin", "admin123", "10.0.0.2");

        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void req1_2_anUnknownUsernameIsBlockedWithTheSameResponse() throws Exception {
        failLogins("no-existe", 5, "10.0.0.1");

        MvcResult unknown = login("no-existe", "lo-que-sea", "10.0.0.2");
        failLogins("admin", 5, "10.0.0.3");
        MvcResult known = login("admin", "lo-que-sea", "10.0.0.4");

        assertThat(unknown.getResponse().getStatus()).isEqualTo(429);
        assertThat(known.getResponse().getStatus()).isEqualTo(429);
        assertThat(JsonPath.<String>read(bodyOf(unknown), "$.message"))
                .isEqualTo(JsonPath.<String>read(bodyOf(known), "$.message"));
    }

    @Test
    void req1_4_differentCaseSharesTheCounter() throws Exception {
        failLogins("admin", 3, "10.0.0.1");
        failLogins("ADMIN", 2, "10.0.0.2");

        assertThat(login(" Admin ", "admin123", "10.0.0.3").getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void req1_5_theBlockLiftsAfter15Minutes() throws Exception {
        failLogins("admin", 5, "10.0.0.1");
        assertThat(login("admin", "admin123", "10.0.0.2").getResponse().getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(15));

        assertThat(login("admin", "admin123", "10.0.0.2").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void req1_4b_malformedOrEmptyBodiesAre400AndDoNotCount() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/auth/login").with(from("10.0.0.1")).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andExpect(status().isBadRequest());
            mockMvc.perform(post("/auth/login").with(from("10.0.0.1")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"\",\"password\":\"x\"}")).andExpect(status().isBadRequest());
            mockMvc.perform(post("/auth/login").with(from("10.0.0.1")).contentType(MediaType.APPLICATION_JSON)
                    .content("no es json")).andExpect(status().isBadRequest());
        }

        assertThat(login("admin", "admin123", "10.0.0.1").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void req2_1_theTwentyFirstAttemptFromAnIpIs429WhateverTheUser() throws Exception {
        for (int i = 1; i <= 20; i++) {
            assertThat(login("usuario" + i, "incorrecta", "10.0.0.1").getResponse().getStatus()).isEqualTo(401);
        }

        assertThat(login("admin", "admin123", "10.0.0.1").getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void req2_2_anotherIpIsNotAffected() throws Exception {
        for (int i = 1; i <= 20; i++) {
            login("usuario" + i, "incorrecta", "10.0.0.1");
        }

        assertThat(login("admin", "admin123", "10.0.0.2").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void req3_1_theBlockedBodyIsTheStandardApiError() throws Exception {
        failLogins("admin", 5, "10.0.0.1");

        mockMvc.perform(post("/auth/login").with(from("10.0.0.1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.message").value(TooManyAttemptsException.MESSAGE))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }

    @Test
    void req3_1b_userAndIpBlocksHaveTheSameMessage() throws Exception {
        failLogins("admin", 5, "10.0.0.1");
        for (int i = 1; i <= 20; i++) {
            login("usuario" + i, "incorrecta", "10.0.0.9");
        }

        String byUser = bodyOf(login("admin", "admin123", "10.0.0.2"));
        String byIp = bodyOf(login("teamleader", "teamleader123", "10.0.0.9"));

        assertThat(JsonPath.<String>read(byUser, "$.message")).isEqualTo(JsonPath.<String>read(byIp, "$.message"));
    }

    @Test
    void req3_2_retryAfterIsTheRemainingTimeInSeconds() throws Exception {
        failLogins("admin", 5, "10.0.0.1");
        clock.advance(Duration.ofMinutes(5));

        MvcResult blocked = login("admin", "admin123", "10.0.0.2");

        assertThat(blocked.getResponse().getHeader("Retry-After")).isEqualTo("600");
    }

    @Test
    void req3_6_otherEndpointsAreNotLimitedForABlockedIpOrUser() throws Exception {
        String token = JsonPath.read(bodyOf(login("teamleader", "teamleader123", "10.0.0.5")), "$.token");
        failLogins("admin", 5, "10.0.0.1");
        for (int i = 1; i <= 20; i++) {
            login("usuario" + i, "incorrecta", "10.0.0.1");
        }

        mockMvc.perform(get("/auth/me").with(from("10.0.0.1")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/machines").with(from("10.0.0.1")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void req3_5_insistingWhileBlockedDoesNotExtendTheBlock() throws Exception {
        failLogins("admin", 5, "10.0.0.1");
        for (int i = 0; i < 30; i++) {
            assertThat(login("admin", "admin123", "10.0.0.2").getResponse().getStatus()).isEqualTo(429);
        }

        clock.advance(Duration.ofMinutes(15));

        assertThat(login("admin", "admin123", "10.0.0.2").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void req1_3_aSuccessfulLoginResetsTheUserCounter() throws Exception {
        failLogins("admin", 4, "10.0.0.1");
        assertThat(login("admin", "admin123", "10.0.0.1").getResponse().getStatus()).isEqualTo(200);
        failLogins("admin", 4, "10.0.0.1");

        assertThat(login("admin", "admin123", "10.0.0.1").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void nfr1_aBlockedLoginNeverLogsOrReturnsThePasswordItReceived() throws Exception {
        failLogins("admin", 5, "10.0.0.1");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            MvcResult blocked = login("admin", SECRET_MARKER, "10.0.0.2");
            List<ILoggingEvent> events = List.copyOf(appender.list);

            assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
            assertThat(bodyOf(blocked)).doesNotContain(SECRET_MARKER);
            assertThat(events).noneMatch(e -> e.getFormattedMessage().contains(SECRET_MARKER));
        } finally {
            root.detachAppender(appender);
        }
    }
}
