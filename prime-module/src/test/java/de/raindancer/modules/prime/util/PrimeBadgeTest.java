package de.raindancer.modules.prime.util;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PrimeBadgeTest {

    @Test
    @DisplayName("where badges are shown, the default Prime badge is the pixel one Ranks registered for its tag")
    void pixel() {
        List<String> asked = new ArrayList<>();
        Component glyph = Component.text("豈");
        Component badge = PrimeBadge.of(tag -> {
            asked.add(tag);
            return Optional.of(glyph);
        }, true);
        assertThat(badge).isEqualTo(glyph);
        assertThat(asked).containsExactly(PrimeBadge.DEFAULT_TAG);
    }

    @Test
    @DisplayName("without badges, or when none can be made, it is the gradient [PRIME] text")
    void text() {
        assertThat(PrimeBadge.plain(PrimeBadge.of(tag -> Optional.of(Component.text("x")), false))).isEqualTo("[PRIME]");
        assertThat(PrimeBadge.plain(PrimeBadge.of(tag -> Optional.empty(), true))).isEqualTo("[PRIME]");
    }
}
