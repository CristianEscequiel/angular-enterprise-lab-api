package com.enterpriselab.api.auth.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Spec 06, REQ-1 a REQ-4: límite de intentos de {@code POST /auth/login}, por usuario y por IP,
 * en memoria (hay una sola instancia; se pierde al reiniciar).
 *
 * <p>Cada clave tiene una entrada con {@code reserved} (fallos más intentos en vuelo) y
 * {@code failures} (solo fallos). {@link #acquire} reserva un cupo <em>antes</em> de verificar la
 * contraseña: así, por mucho que lleguen intentos a la vez, no pasan más del umbral a la
 * verificación. Una clave está bloqueada si {@code blockedUntil} es futuro o si
 * {@code reserved >= max}. Al alcanzar {@code max} fallos, la clave queda bloqueada
 * {@code window} desde ese fallo (una sola vez: insistir no lo extiende). La ventana empieza en
 * el primer intento que deja rastro: una entrada se elimina al quedar sin reservas, de modo que
 * un login exitoso aislado no abre ventana.
 *
 * <p>Los métodos públicos son {@code synchronized}: la sección crítica es de microsegundos y es
 * más simple de razonar que {@code compute} anidados sobre dos claves con rollback.
 */
public class LoginAttemptLimiter {

    private static final Duration PURGE_EVERY = Duration.ofMinutes(1);

    private final int maxPerUser;
    private final int maxPerIp;
    private final Duration window;
    private final Clock clock;

    private final Map<String, Entry> entries = new HashMap<>();
    private Instant lastPurge;

    public LoginAttemptLimiter(int maxPerUser, int maxPerIp, Duration window, Clock clock) {
        this.maxPerUser = maxPerUser;
        this.maxPerIp = maxPerIp;
        this.window = window;
        this.clock = clock;
        this.lastPurge = clock.instant();
    }

    /**
     * Reserva un cupo para el usuario y la IP, o lanza {@link TooManyAttemptsException} sin
     * modificar nada si cualquiera de las dos claves está bloqueada.
     */
    public synchronized void acquire(String username, String ip) {
        Instant now = clock.instant();
        purgeIfDue(now);
        String userKey = userKey(username);
        String ipKey = ipKey(ip);

        Duration wait = max(waitFor(ipKey, now), waitFor(userKey, now));
        if (wait != null) {
            throw new TooManyAttemptsException(wait);
        }
        reserve(ipKey, now);
        reserve(userKey, now);
    }

    /** El intento reservado terminó en credenciales incorrectas. */
    public synchronized void recordFailure(String username, String ip) {
        Instant now = clock.instant();
        fail(ipKey(ip), now);
        fail(userKey(username), now);
    }

    /**
     * Login válido: reinicia el contador del usuario y libera el cupo de la IP (un login válido
     * no es un fallo; detrás de un NAT no debe consumir el cupo de los demás).
     */
    public synchronized void recordSuccess(String username, String ip) {
        entries.remove(userKey(username));
        unreserve(ipKey(ip));
    }

    /** El intento terminó por un error del servidor: no se penaliza al usuario ni a la IP. */
    public synchronized void release(String username, String ip) {
        unreserve(ipKey(ip));
        unreserve(userKey(username));
    }

    /** Cantidad de claves en memoria. */
    public synchronized int size() {
        return entries.size();
    }

    /** Descarta las entradas vencidas. */
    public synchronized void purgeExpired() {
        Instant now = clock.instant();
        for (Iterator<Entry> it = entries.values().iterator(); it.hasNext();) {
            if (it.next().isExpired(now)) {
                it.remove();
            }
        }
        lastPurge = now;
    }

    /** Solo para tests. */
    public synchronized void reset() {
        entries.clear();
        lastPurge = clock.instant();
    }

    // --- Internas --------------------------------------------------------------------------------

    private void purgeIfDue(Instant now) {
        if (Duration.between(lastPurge, now).compareTo(PURGE_EVERY) >= 0) {
            purgeExpired();
        }
    }

    /** Espera restante si la clave está bloqueada; {@code null} si no. */
    private Duration waitFor(String key, Instant now) {
        Entry entry = live(key, now);
        if (entry == null) {
            return null;
        }
        if (entry.blockedUntil != null && now.isBefore(entry.blockedUntil)) {
            return Duration.between(now, entry.blockedUntil);
        }
        if (entry.reserved >= maxFor(key)) {
            return Duration.between(now, entry.windowStart.plus(window));
        }
        return null;
    }

    private void reserve(String key, Instant now) {
        Entry entry = live(key, now);
        if (entry == null) {
            entry = new Entry(now);
            entries.put(key, entry);
        }
        entry.reserved++;
    }

    private void fail(String key, Instant now) {
        Entry entry = live(key, now);
        if (entry == null) {
            return;
        }
        entry.failures++;
        if (entry.failures >= maxFor(key) && entry.blockedUntil == null) {
            entry.blockedUntil = now.plus(window);
        }
    }

    private void unreserve(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return;
        }
        entry.reserved--;
        if (entry.reserved <= 0) {
            entries.remove(key);
        }
    }

    /** La entrada de la clave, o {@code null} si no existe o ya venció (en cuyo caso se descarta). */
    private Entry live(String key, Instant now) {
        Entry entry = entries.get(key);
        if (entry != null && entry.isExpired(now)) {
            entries.remove(key);
            return null;
        }
        return entry;
    }

    private int maxFor(String key) {
        return key.startsWith("u:") ? maxPerUser : maxPerIp;
    }

    private static Duration max(Duration a, Duration b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String userKey(String username) {
        return "u:" + (username == null ? "" : username.strip().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "i:" + (ip == null ? "" : ip);
    }

    private final class Entry {
        final Instant windowStart;
        int reserved;
        int failures;
        Instant blockedUntil;

        Entry(Instant windowStart) {
            this.windowStart = windowStart;
        }

        boolean isExpired(Instant now) {
            Instant end = blockedUntil != null ? blockedUntil : windowStart.plus(window);
            return !now.isBefore(end);
        }
    }
}
