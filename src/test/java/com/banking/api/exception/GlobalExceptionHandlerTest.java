package com.banking.api.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins down how each exception type becomes an HTTP response, so the status codes documented in
 * the OpenAPI spec cannot silently drift back to a blanket 500.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void missingRecordBecomesNotFound() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Client not found"));
    }

    @Test
    void wrongStateBecomesConflict() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Client is not in pending state"));
    }

    @Test
    void unusableInputBecomesBadRequest() throws Exception {
        mockMvc.perform(get("/probe/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid Request"))
                .andExpect(jsonPath("$.message").value("Unknown roles: [ROLE_NOPE]"));
    }

    @Test
    void businessErrorUsesTheStatusItWasGiven() throws Exception {
        mockMvc.perform(get("/probe/business-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("A user with this email address already exists"));
    }

    @Test
    void businessErrorDefaultsToBadRequest() throws Exception {
        mockMvc.perform(get("/probe/business-error-default"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SOMETHING_INVALID"));
    }

    @Test
    void anythingUnhandledStaysServerError() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new EntityNotFoundException("Client not found");
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new IllegalStateException("Client is not in pending state");
        }

        @GetMapping("/probe/bad-request")
        void badRequest() {
            throw new IllegalArgumentException("Unknown roles: [ROLE_NOPE]");
        }

        @GetMapping("/probe/business-error")
        void businessError() {
            throw new ApiException(
                    "EMAIL_ALREADY_REGISTERED",
                    "A user with this email address already exists",
                    HttpStatus.CONFLICT
            );
        }

        @GetMapping("/probe/business-error-default")
        void businessErrorDefault() {
            throw new ApiException("SOMETHING_INVALID", "Nope");
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new RuntimeException("Keycloak blew up");
        }
    }
}
