package com.enterpriselab.api.workorders.domain;

import java.time.Instant;

/** Quién canceló una orden pendiente y cuándo (sin comentario: no es una {@link ClosingNote}). */
public record Cancellation(long authorId, String authorName, Instant at) {
}
