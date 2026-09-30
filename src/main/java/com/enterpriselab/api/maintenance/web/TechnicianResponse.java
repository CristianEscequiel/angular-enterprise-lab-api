package com.enterpriselab.api.maintenance.web;

import com.enterpriselab.api.maintenance.domain.Technician;

/** Técnico tal como lo ve el cliente: {@code id} como string (ROADMAP D2) y los enums en kebab-case. */
public record TechnicianResponse(String id, String legajo, String firstName, String lastName, String specialty,
        String teamType) {

    static TechnicianResponse from(Technician technician) {
        return new TechnicianResponse(
                String.valueOf(technician.id()),
                technician.legajo(),
                technician.firstName(),
                technician.lastName(),
                technician.specialty().toValue(),
                technician.teamType().toValue());
    }
}
