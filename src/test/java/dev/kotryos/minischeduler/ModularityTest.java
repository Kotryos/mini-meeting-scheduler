package dev.kotryos.minischeduler;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void applicationModules_asDeclaredByPackageStructure_respectTheirBoundaries() {
        ApplicationModules.of(MiniSchedulerApplication.class).verify();
    }
}
