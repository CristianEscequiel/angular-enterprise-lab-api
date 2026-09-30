package com.enterpriselab.api.maintenance.persistence;

import com.enterpriselab.api.maintenance.domain.Specialty;
import com.enterpriselab.api.maintenance.domain.Technician;
import com.enterpriselab.api.maintenance.domain.TeamType;

/** Mapper manual entity ↔ dominio (design.md §1: sin MapStruct en esta spec). */
final class TechnicianMapper {

    private TechnicianMapper() {
    }

    static Technician toDomain(TechnicianEntity entity) {
        return new Technician(
                entity.getId(),
                entity.getLegajo(),
                entity.getFirstName(),
                entity.getLastName(),
                Specialty.fromValue(entity.getSpecialty()),
                TeamType.fromValue(entity.getTeamType()));
    }

    /** Entity nueva, sin {@code id}, para un alta. */
    static TechnicianEntity toNewEntity(Technician technician) {
        return new TechnicianEntity(
                technician.legajo(),
                technician.firstName(),
                technician.lastName(),
                technician.specialty().toValue(),
                technician.teamType().toValue());
    }
}
