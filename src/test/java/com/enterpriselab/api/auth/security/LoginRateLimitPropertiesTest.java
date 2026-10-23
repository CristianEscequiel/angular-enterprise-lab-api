package com.enterpriselab.api.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.enterpriselab.api.auth.domain.LoginAttemptLimiter;
import com.enterpriselab.api.auth.domain.TooManyAttemptsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 06, tarea 3: REQ-4.1, 4.1b y 4.2. */
class LoginRateLimitPropertiesTest {

    @Configuration
    @Import(LoginRateLimitConfig.class)
    static class Base {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Base.class);

    private static void failTimes(LoginAttemptLimiter limiter, String user, int times) {
        for (int i = 0; i < times; i++) {
            limiter.acquire(user, "10.0.0." + (i + 1));
            limiter.recordFailure(user, "10.0.0." + (i + 1));
        }
    }

    @Test
    void req4_1_thePerUserThresholdFollowsTheProperty() {
        runner.withPropertyValues("app.security.login-rate-limit.max-attempts-per-user=2").run(context -> {
            LoginAttemptLimiter limiter = context.getBean(LoginAttemptLimiter.class);
            failTimes(limiter, "admin", 2);
            assertThatThrownBy(() -> limiter.acquire("admin", "10.9.9.9")).isInstanceOf(TooManyAttemptsException.class);
        });
    }

    @Test
    void req4_1_thePerIpThresholdFollowsTheProperty() {
        runner.withPropertyValues("app.security.login-rate-limit.max-attempts-per-ip=2").run(context -> {
            LoginAttemptLimiter limiter = context.getBean(LoginAttemptLimiter.class);
            for (int i = 1; i <= 2; i++) {
                limiter.acquire("u" + i, "10.0.0.1");
                limiter.recordFailure("u" + i, "10.0.0.1");
            }
            assertThatThrownBy(() -> limiter.acquire("otro", "10.0.0.1")).isInstanceOf(TooManyAttemptsException.class);
        });
    }

    @Test
    void req4_1_theWindowFollowsTheProperty() {
        runner.withPropertyValues("app.security.login-rate-limit.max-attempts-per-user=1",
                "app.security.login-rate-limit.window=2m").run(context -> {
            LoginAttemptLimiter limiter = context.getBean(LoginAttemptLimiter.class);
            failTimes(limiter, "admin", 1);
            assertThatThrownBy(() -> limiter.acquire("admin", "10.9.9.9"))
                    .isInstanceOfSatisfying(TooManyAttemptsException.class,
                            e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMinutes(2)));
        });
    }

    @Test
    void req4_1b_withoutPropertiesTheDefaultsAre5And20And15Minutes() {
        runner.run(context -> {
            LoginRateLimitProperties properties = context.getBean(LoginRateLimitProperties.class);
            assertThat(properties.maxAttemptsPerUser()).isEqualTo(5);
            assertThat(properties.maxAttemptsPerIp()).isEqualTo(20);
            assertThat(properties.window()).isEqualTo(Duration.ofMinutes(15));
        });
    }

    @Test
    void req4_2_aZeroOrNegativeThresholdFailsNamingTheProperty() {
        runner.withPropertyValues("app.security.login-rate-limit.max-attempts-per-user=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure()))
                    .contains("app.security.login-rate-limit.max-attempts-per-user");
        });
        runner.withPropertyValues("app.security.login-rate-limit.max-attempts-per-ip=-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure()))
                    .contains("app.security.login-rate-limit.max-attempts-per-ip");
        });
    }

    @Test
    void req4_2_aZeroOrInvalidWindowFailsNamingTheProperty() {
        runner.withPropertyValues("app.security.login-rate-limit.window=0s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("app.security.login-rate-limit.window");
        });
        runner.withPropertyValues("app.security.login-rate-limit.window=abc").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("app.security.login-rate-limit.window");
        });
    }

    @Test
    void req4_2_aNonNumericThresholdFailsNamingTheProperty() {
        runner.withPropertyValues("app.security.login-rate-limit.max-attempts-per-user=abc").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure()))
                    .contains("app.security.login-rate-limit.max-attempts-per-user");
        });
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            all.append(current.getMessage()).append('\n');
        }
        return all.toString();
    }
}
