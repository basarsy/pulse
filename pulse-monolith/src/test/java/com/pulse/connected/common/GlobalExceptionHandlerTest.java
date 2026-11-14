package com.pulse.connected.common;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ConflictingController {
        @PostMapping("/conflict")
        void conflict() {
            throw new ObjectOptimisticLockingFailureException("RemoteCommand", "id");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConflictingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    /** E.g. a cancel that lost the race against the vehicle's result: the client should re-read. */
    @Test
    void concurrentModification_IsConflictNotServerError() throws Exception {
        mvc.perform(post("/conflict")).andExpect(status().isConflict());
    }
}
