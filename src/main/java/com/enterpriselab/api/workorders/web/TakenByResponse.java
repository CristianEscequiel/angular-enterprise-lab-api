package com.enterpriselab.api.workorders.web;

import java.time.Instant;

import com.enterpriselab.api.workorders.domain.TakenBy;

/** Dueño de la orden: id de usuario como string, nombre (foto al tomarla) e instante. */
public record TakenByResponse(String id, String name, Instant at) {

    static TakenByResponse from(TakenBy takenBy) {
        return new TakenByResponse(String.valueOf(takenBy.userId()), takenBy.name(), takenBy.at());
    }
}
