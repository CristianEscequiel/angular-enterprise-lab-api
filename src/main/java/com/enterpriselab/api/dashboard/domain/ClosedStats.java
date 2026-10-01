package com.enterpriselab.api.dashboard.domain;

import java.math.BigDecimal;

/**
 * Cerradas dentro del período. {@code averageResolutionMinutes} es el promedio de las
 * {@code completed}, sin redondear, o {@code null} si no hay ninguna.
 */
public record ClosedStats(long completed, long cancelled, BigDecimal averageResolutionMinutes) {
}
