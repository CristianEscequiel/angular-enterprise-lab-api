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
    void conflictWithDetailsSerializesThem() throws Exception {
        mockMvc.perform(get("/test/conflict-details"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORK_ORDER_NOT_PENDING"))
                .andExpect(jsonPath("$.details.status").value("in-progress"));
    }

    @Test
    void conflictWithoutDetailsOmitsTheField() throws Exception {
        mockMvc.perform(get("/test/conflict")).andExpect(jsonPath("$.details").doesNotExist());
        mockMvc.perform(get("/test/conflict-empty-details")).andExpect(jsonPath("$.details").doesNotExist());
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

    @Test
    void req3_1_aBlockedLoginRespondsTooManyRequestsWithTheStandardBody() throws Exception {
        mockMvc.perform(get("/test/too-many").param("millis", "5000"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/too-many"));
    }

    @Test
    void req3_1b_theMessageIsTheSameWhateverTheWaitIs() throws Exception {
        String first = mockMvc.perform(get("/test/too-many").param("millis", "1000")).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String second = mockMvc.perform(get("/test/too-many").param("millis", "900000")).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThat(com.jayway.jsonpath.JsonPath.<String>read(first, "$.message"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(second, "$.message"))
                .isEqualTo(com.enterpriselab.api.auth.domain.TooManyAttemptsException.MESSAGE);
    }

    @Test
    void req3_2_retryAfterIsAnIntegerNumberOfSecondsRoundedUpAndAtLeastOne() throws Exception {
        mockMvc.perform(get("/test/too-many").param("millis", "200"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "1"));
        mockMvc.perform(get("/test/too-many").param("millis", "1001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "2"));
        mockMvc.perform(get("/test/too-many").param("millis", "900000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "900"));
    }

    @Test
    void req10_1_aMissingRequiredParameterIs400WithTheParameterNameInDetails() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/required-param"))
                .andExpect(jsonPath("$.details.name").value("Es obligatorio"));
    }

    @Test
    void req10_2_aParameterOfTheWrongTypeIs400WithTheParameterNameInDetails() throws Exception {
        mockMvc.perform(get("/test/typed-param").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.count").value("Valor inválido"));
    }

    @Test
    void req8_aMethodTheResourceDoesNotSupportIs405WithAllow() throws Exception {
        mockMvc.perform(post("/test/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/boom"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Allow", containsString("GET")));
    }

    @Test
    void req9_aContentTypeTheEndpointDoesNotAcceptIs415() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/validate"));
    }

    @Test
    void req12_1_anUnexpectedExceptionIsStillAGeneric500WithoutInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado"))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.message").value(not(containsString("boom"))))
                .andExpect(content().string(not(containsString("at com."))));
    }
}
