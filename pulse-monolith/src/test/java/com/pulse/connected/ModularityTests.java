package com.pulse.connected;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the module boundaries of the monolith: no dependency cycles between
 * modules and no access to another module's internal (sub-)packages.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(PulseApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
