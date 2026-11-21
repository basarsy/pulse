package com.pulse.connected.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ConflictingController {
        @PostMapping("/conflict")
        void conflict() {
            throw new ObjectOptimisticLockingFailureException("RemoteCommand", "id");
        }

        @PostMapping("/constraint")
        void constraint() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\"");
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

    /** E.g. two registrations of one email racing past the existence check: 409, no SQL in the body. */
    @Test
    void constraintViolation_IsConflictWithoutDetails() throws Exception {
        mvc.perform(post("/constraint"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("users_email_key"))));
    }
}
