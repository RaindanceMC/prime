package de.raindancer.modules.prime.service;

import de.raindancer.core.ui.statue.StatueBoard;
import de.raindancer.core.ui.statue.StatueBoards;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Prime's statue board for Core's statues ({@code /eco statue place prime}): everybody with Prime right now,
 * longest-running first, on the ground with the PRIME mark, taking turns when there are more members than statues.
 */
public final class PrimeStatues {

    /** Ranks' prime tag as shipped: Core draws a tag as its badge only when it is exactly the one Ranks made one of. */
    private static final String MARK = "<gradient:#ff3d9a:#ff9ad5><bold>[PRIME]</bold></gradient>";

    private PrimeStatues() {
    }

    /**
     * @param members who has Prime now, longest-running first (Core's PrimeMembers)
     * @param names   a player's name; somebody who paid before ever joining has none, and no statue yet
     */
    public static StatueBoard board(Supplier<List<UUID>> members, Function<UUID, Optional<String>> names) {
        return StatueBoard.thanks("prime", "<gradient:#ff3d9a:#ffd27a><bold>Our Prime members</bold></gradient>",
                "", MARK, "Prime members",
                () -> StatueBoards.named(members.get(), names));
    }
}
