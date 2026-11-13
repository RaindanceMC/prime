package de.raindancer.modules.prime;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.prime.command.PrimeCommand;

import java.util.List;

/**
 * Declared at bootstrap, filled in when the module starts — Paper registers commands before any
 * plugin is enabled, so the command points at {@link #require} until then.
 */
public final class PrimeCommands {

    private static volatile PrimeServices services;

    private PrimeCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(ModuleCommand.of("prime", "Your Prime subscription, its perks, and map art",
                        new PrimeCommand(PrimeCommands::require))
                .taking("(nothing) — your Prime and what it gives",
                        "mapart <https-link> <width>x<height> — ask for an image as map art (Prime)",
                        "mapart list | approve <id> | refuse <id> [reason] — staff",
                        "grant <player|uuid> <days> | revoke <player|uuid> | info <player|uuid> — the store, the owner"));
    }

    static void ready(PrimeServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
    }

    private static PrimeServices require() {
        PrimeServices live = services;
        if (live == null) {
            throw new IllegalStateException("the prime module is not running");
        }
        return live;
    }
}
