package com.enterpriselab.api.workorders.domain;

import java.util.List;

/**
 * Dónde está una parte: su máquina y los nombres de la cadena de ancestros desde
 * la parte de primer nivel hasta la parte misma, en ese orden.
 */
public record PartLocation(long machineId, List<String> pathNames) {
}
