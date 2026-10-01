package com.enterpriselab.api.workorders.web;

import com.enterpriselab.api.workorders.domain.CloseWorkOrderCommand;

/**
 * Cuerpo del cierre. Todo {@code String}, sin Bean Validation: lo valida el dominio
 * después de autorizar. Un {@code authorId} o {@code authorName} extra lo descarta
 * Jackson: el autor sale del token.
 */
public record WorkOrderCloseRequest(String outcome, String comment) {

    CloseWorkOrderCommand toCommand() {
        return new CloseWorkOrderCommand(outcome, comment);
    }
}
