package com.enterpriselab.api.workorders.web;

import java.time.Instant;

import com.enterpriselab.api.workorders.domain.ClosingNote;

/** Nota de cierre de una orden completada o cancelada. */
public record ClosingNoteResponse(String comment, String authorId, String authorName, Instant at) {

    static ClosingNoteResponse from(ClosingNote note) {
        return new ClosingNoteResponse(note.comment(), String.valueOf(note.authorId()), note.authorName(),
                note.at());
    }
}
