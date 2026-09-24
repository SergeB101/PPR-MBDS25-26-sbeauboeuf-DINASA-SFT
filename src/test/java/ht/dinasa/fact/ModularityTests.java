package ht.dinasa.fact;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    ApplicationModules modules = ApplicationModules.of(DinasaFactApiApplication.class);

    @Test
    void les_modules_respectent_leurs_frontieres() {
        modules.verify();
    }

    @Test
    void generer_la_documentation_des_modules() {
        new Documenter(modules).writeDocumentation();
    }
}