package com.enterpriselab.api.maintenance.domain;

import java.util.List;

/** Entrada cruda de alta y edición de un equipo, sin validar (ver {@link TechnicianCommand}). */
public record TeamCommand(String name, String type, List<String> memberLegajos) {
}
