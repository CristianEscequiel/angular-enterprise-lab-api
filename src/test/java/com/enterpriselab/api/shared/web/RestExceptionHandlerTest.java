package com.enterpriselab.api.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.enterpriselab.api.auth.security.JsonAuthenticationEntryPoint;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REQ-13: cada camino de error controlado por {@link RestExceptionHandler}
 * responde con la misma forma {@code {code,message,timestamp,path}} y el
 * HTTP status esperado — nunca el stacktrace por defecto de Spring.
 *
 * <p>{@code addFilters = false}: sin SecurityConfig propia (tarea 16),
 * {@code @WebMvcTest} aplica por defecto un filtro de seguridad
 * deny-all/HTTP-Basic solo porque spring-security está en el classpath
 * (a diferencia del contexto completo, ver HealthIT/tarea 5) — ruido ajeno
 * a lo que prueba este test.
 */
@WebMvcTest(controllers = TestExceptionsController.class)
@AutoConfigureMockMvc(addFilters = false)
class RestExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrorRespondsWithConsistentBody() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/validate"))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void domainValidationFailureRespondsWithDetailsPerField() throws Exception {
        mockMvc.perform(get("/test/validation-failed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/validation-failed"))
                .andExpect(jsonPath("$.details.legajo").value("Debe tener entre 1 y 8 dígitos"))
                .andExpect(jsonPath("$.details.firstName").value("Es obligatorio"));
    }

    @Test
    void invalidReferenceRespondsBadRequestWithItsOwnCode() throws Exception {
        mockMvc.perform(get("/test/invalid-reference"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TECHNICIAN"))
                .andExpect(jsonPath("$.message").value("No existe el técnico con legajo 9999"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/invalid-reference"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    /** REQ-24: mismo código y mismo mensaje que el entry point para un token inválido; no filtra el username. */
    @Test
    void anUnknownSessionUserRespondsUnauthorizedWithTheInvalidTokenMessage() throws Exception {
        mockMvc.perform(get("/test/unknown-session-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(JsonAuthenticationEntryPoint.MESSAGE))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/unknown-session-user"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(not(containsString("ghost"))));
    }

    @Test
    void domainNotFoundRespondsNotFound() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No existe el técnico con legajo 9999"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    @Test
    void conflictRespondsConflictWithItsOwnCode() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LEGAJO"))
                .andExpect(jsonPath("$.message").value("Ya existe un técnico con legajo 1001"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/conflict"));
    }

    @Test
    void malformedJsonBodyRespondsValidationError() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/validate"));
    }

    @Test
    void missingJsonBodyRespondsValidationError() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unmappedRouteRespondsNotFoundWithConsistentBody() throws Exception {
        mockMvc.perform(get("/esta-ruta-no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/esta-ruta-no-existe"));
    }

    @Test
    void unexpectedExceptionRespondsInternalErrorWithoutStacktrace() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/boom"))
                .andExpect(content().string(not(containsString("com.enterpriselab.api"))));
    }
}
