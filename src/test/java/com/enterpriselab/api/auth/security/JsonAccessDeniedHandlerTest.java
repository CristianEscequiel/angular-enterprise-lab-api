package com.enterpriselab.api.auth.security;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-11 / REQ-13: el 403 del filtro sale con la forma {@code ApiError}. */
class JsonAccessDeniedHandlerTest {

    @Test
    @SuppressWarnings("unchecked")
    void writesAForbiddenApiError() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-only");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAccessDeniedHandler(mapper).handle(request, response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        Map<String, Object> body = mapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body).containsEntry("code", "FORBIDDEN")
                .containsEntry("message", JsonAccessDeniedHandler.MESSAGE)
                .containsEntry("path", "/admin-only")
                .containsKey("timestamp");
    }
}
