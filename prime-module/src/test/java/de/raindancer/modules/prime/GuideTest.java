package de.raindancer.modules.prime;

import de.raindancer.core.ui.help.HelpLint;
import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    @Test
    void theGuideIsComplete() {
        assertThat(HelpLint.of(Path.of("src/main/resources/rains-help"))
                .module("prime")
                .settings(PrimeSettings.class, PrimeSettings.DEFAULTS)
                .commands(new PrimeModule().commands().stream().map(ModuleCommand::name).toList())
                .problems()).isEmpty();
    }
}
