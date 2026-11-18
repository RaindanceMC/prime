package de.raindancer.modules.prime.service;

import de.raindancer.core.ui.statue.StatueBoard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrimeStatuesTest {

    private static final UUID ADA = UUID.randomUUID();
    private static final UUID BO = UUID.randomUUID();
    private static final UUID PAID_BEFORE_JOINING = UUID.randomUUID();

    @Test
    @DisplayName("the prime board thanks Prime members, longest-running first; somebody never seen has no statue yet")
    void board() {
        Map<UUID, String> names = Map.of(ADA, "Ada", BO, "Bo");
        StatueBoard board = PrimeStatues.board(() -> List.of(BO, PAID_BEFORE_JOINING, ADA),
                id -> Optional.ofNullable(names.get(id)));
        assertThat(board.id()).isEqualTo("prime");
        assertThat(board.thankYou()).isTrue();
        assertThat(board.ranks()).isEqualTo("Prime members");
        assertThat(board.title(4, "Ada")).contains("PRIME").contains("Ada");
        assertThat(board.heading()).contains("Prime");
        assertThat(board.standings().get()).extracting(StatueBoard.Standing::name).containsExactly("Bo", "Ada");
        assertThat(board.caption()).as("no line under the name").isEmpty();
        assertThat(board.mark()).as("exactly Ranks' prime tag, so Core draws its badge")
                .isEqualTo("<gradient:#ff3d9a:#ff9ad5><bold>[PRIME]</bold></gradient>");
    }
}
