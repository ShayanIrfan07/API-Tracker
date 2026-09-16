package com.apitracker.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unauthorizedReturns401() throws Exception {
        mockMvc.perform(get("/probe/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void conflictReturns409() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void illegalStateReturns409() throws Exception {
        mockMvc.perform(get("/probe/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void badRequestReturns400() throws Exception {
        mockMvc.perform(get("/probe/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void unexpectedErrorReturns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestController
    static class ExceptionProbeController {

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new ResourceNotFoundException("MonitoredApi", UUID.randomUUID());
        }

        @GetMapping("/probe/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Invalid credentials");
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new ConflictException("Duplicate API name");
        }

        @GetMapping("/probe/illegal-state")
        void illegalState() {
            throw new IllegalStateException("Check already in progress");
        }

        @GetMapping("/probe/bad-request")
        void badRequest() {
            throw new IllegalArgumentException("URL must use http or https");
        }

        @GetMapping("/probe/unexpected")
        void unexpected() {
            throw new RuntimeException("simulated failure");
        }
    }
}
