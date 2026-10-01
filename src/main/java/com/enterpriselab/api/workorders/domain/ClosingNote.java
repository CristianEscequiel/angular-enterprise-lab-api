package com.enterpriselab.api.workorders.domain;

import java.time.Instant;

/** Nota con la que se cerró una orden; {@code authorName} es una foto del nombre. La escribe la spec 04. */
public record ClosingNote(String comment, long authorId, String authorName, Instant at) {
}
