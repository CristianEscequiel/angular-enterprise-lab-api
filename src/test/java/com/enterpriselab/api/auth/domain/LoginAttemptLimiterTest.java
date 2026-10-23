package com.enterpriselab.api.auth.domain;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 06, tarea 3: REQ-1, REQ-2, REQ-3.5 y REQ-4.3/4.4 sobre el limitador de dominio. */
class LoginAttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String IP = "10.0.0.1";

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at("2026-10-22T12:00:00Z");
        limiter = new LoginAttemptLimiter(5, 20, WINDOW, clock);
    }

    /** Un intento que termina en credenciales incorrectas. */
    private void failedAttempt(String username, String ip) {
        limiter.acquire(username, ip);
        limiter.recordFailure(username, ip);
    }

    private Duration blockedFor(String username, String ip) {
        try {
            limiter.acquire(username, ip);
            limiter.release(username, ip);
            return null;
        } catch (TooManyAttemptsException blocked) {
            return blocked.retryAfter();
        }
    }

    @Test
    void req1_1_theFifthFailedAttemptOfAUserStillPasses() {
        for (int i = 1; i <= 4; i++) {
            failedAttempt("admin", "10.0.0." + i);
        }

        assertThatCode(() -> failedAttempt("admin", "10.0.0.5")).doesNotThrowAnyException();
    }

    @Test
    void req1_1b_theSixthAttemptIsBlockedEvenIfItWouldSucceed() {
        for (int i = 1; i <= 5; i++) {
            failedAttempt("admin", "10.0.0." + i);
        }

        assertThatThrownBy(() -> limiter.acquire("admin", "10.0.0.9"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void req1_2_anUnknownUsernameIsBlockedLikeAnExistingOne() {
        for (int i = 1; i <= 5; i++) {
            failedAttempt("no-existe", "10.0.0." + i);
        }

        assertThat(blockedFor("no-existe", "10.0.0.9")).isEqualTo(WINDOW);
        assertThat(blockedFor("admin", "10.0.0.9")).isNull();
    }

    @Test
    void req1_3_aSuccessResetsTheUserCounter() {
        for (int i = 1; i <= 4; i++) {
            failedAttempt("admin", IP);
        }
        limiter.acquire("admin", IP);
        limiter.recordSuccess("admin", IP);
        for (int i = 1; i <= 4; i++) {
            failedAttempt("admin", IP);
        }

        assertThat(blockedFor("admin", IP)).isNull();
    }

    @Test
    void req1_4_caseAndSurroundingSpacesShareTheCounter() {
        failedAttempt("Admin", "10.0.0.1");
        failedAttempt(" admin ", "10.0.0.2");
        failedAttempt("ADMIN", "10.0.0.3");
        failedAttempt("admin", "10.0.0.4");
        failedAttempt("aDmIn", "10.0.0.5");

        assertThat(blockedFor("admin", "10.0.0.6")).isNotNull();
    }

    @Test
    void req1_5_theBlockLiftsExactly15MinutesAfterTheFailureThatCausedIt() {
        for (int i = 1; i <= 5; i++) {
            failedAttempt("admin", "10.0.0." + i);
        }

        clock.advance(WINDOW.minusSeconds(1));
        assertThat(blockedFor("admin", "10.0.0.9")).isEqualTo(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(blockedFor("admin", "10.0.0.9")).isNull();
    }

    @Test
    void req2_1_theTwentyFirstAttemptFromAnIpIsBlockedWhateverTheUser() {
        for (int i = 1; i <= 20; i++) {
            failedAttempt("usuario" + i, IP);
        }

        assertThatThrownBy(() -> limiter.acquire("otro-con-clave-correcta", IP))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void req2_2_anotherIpIsNotAffected() {
        for (int i = 1; i <= 20; i++) {
            failedAttempt("usuario" + i, IP);
        }

        assertThat(blockedFor("otro", "10.0.0.2")).isNull();
    }

    @Test
    void req2_4_theIpBlockLiftsAfter15Minutes() {
        for (int i = 1; i <= 20; i++) {
            failedAttempt("usuario" + i, IP);
        }
        clock.advance(WINDOW);

        assertThat(blockedFor("otro", IP)).isNull();
    }

    @Test
    void req3_5_insistingWhileBlockedNeitherCountsNorExtendsTheBlock() {
        for (int i = 1; i <= 5; i++) {
            failedAttempt("admin", "10.0.0." + i);
        }
        clock.advance(Duration.ofMinutes(5));
        Duration before = blockedFor("admin", "10.0.0.9");

        for (int i = 0; i < 50; i++) {
            blockedFor("admin", "10.0.0.9");
        }

        assertThat(blockedFor("admin", "10.0.0.9")).isEqualTo(before).isEqualTo(Duration.ofMinutes(10));
        assertThat(blockedFor("otro", "10.0.0.9")).as("la IP 10.0.0.9 no acumuló fallos").isNull();
    }

    @Test
    void theRetryAfterIsTheLongerOfTheUserAndTheIpBlock() {
        for (int i = 1; i <= 5; i++) {
            failedAttempt("admin", "10.0.0." + i);
        }
        clock.advance(Duration.ofMinutes(10));
        for (int i = 1; i <= 20; i++) {
            failedAttempt("usuario" + i, "10.1.1.1");
        }

        // admin: le faltan 5 minutos; la IP 10.1.1.1: 15.
        assertThat(blockedFor("admin", "10.1.1.1")).isEqualTo(WINDOW);
    }

    @Test
    void req4_3_expiredEntriesAreDroppedFromMemory() {
        for (int i = 1; i <= 5; i++) {
            failedAttempt("usuario" + i, "10.0.0." + i);
        }
        assertThat(limiter.size()).isPositive();

        clock.advance(WINDOW);
        limiter.purgeExpired();

        assertThat(limiter.size()).isZero();
    }

    @Test
    void req4_3_acquireAlsoPurgesOnItsOwnAtMostOncePerMinute() {
        failedAttempt("usuario", IP);
        clock.advance(WINDOW.plusMinutes(2));

        limiter.acquire("otro", "10.0.0.2");

        assertThat(limiter.size()).as("solo las claves del intento nuevo").isEqualTo(2);
    }

    @Test
    void req4_4_concurrentAttemptsNeverLetMoreThanTheThresholdThrough() throws Exception {
        LoginAttemptLimiter strict = new LoginAttemptLimiter(5, 1000, WINDOW, clock);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger passed = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    strict.acquire("admin", IP);
                    passed.incrementAndGet();
                } catch (TooManyAttemptsException e) {
                    blocked.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        assertThat(passed.get()).isEqualTo(5);
        assertThat(blocked.get()).isEqualTo(45);
    }

    @Test
    void aSuccessThatLeavesNoReservationsRemovesTheEntryAndOpensNoWindow() {
        limiter.acquire("admin", IP);
        limiter.recordSuccess("admin", IP);

        assertThat(limiter.size()).isZero();
    }

    @Test
    void releaseGivesBackTheReservationsWithoutCountingAFailure() {
        for (int i = 1; i <= 10; i++) {
            limiter.acquire("admin", IP);
            limiter.release("admin", IP);
        }

        assertThat(limiter.size()).isZero();
        assertThat(blockedFor("admin", IP)).isNull();
    }

    @Test
    void aLegitimateLoginInFlightDoesNotBringTheBlockForward() {
        for (int i = 1; i <= 3; i++) {
            failedAttempt("admin", "10.0.0." + i);
        }
        limiter.acquire("admin", "10.0.0.9"); // en vuelo: reserved = 4
        limiter.acquire("admin", "10.0.0.8"); // en vuelo: reserved = 5
        limiter.recordFailure("admin", "10.0.0.8"); // failures = 4: todavía no hay blockedUntil

        // El cupo está agotado mientras haya intentos en vuelo, hasta el fin de la ventana.
        assertThat(blockedFor("admin", "10.0.0.7")).isEqualTo(WINDOW);

        limiter.recordSuccess("admin", "10.0.0.9"); // el legítimo termina bien y reinicia el usuario
        assertThat(blockedFor("admin", "10.0.0.7")).isNull();
    }
}
