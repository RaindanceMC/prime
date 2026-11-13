package de.raindancer.modules.prime;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.prime.listener.JoinListener;
import de.raindancer.modules.prime.rules.MapArtReviews;
import de.raindancer.modules.prime.service.MapArtService;
import de.raindancer.modules.prime.store.MapArtBook;
import de.raindancer.modules.prime.util.PermissionNodes;

import java.util.List;

/**
 * Prime, the server's subscription, as players meet it: {@code /prime} with what it gives, and map art. Who is Prime
 * is Core's ({@code PrimeMembers}, granted by the store through {@code /prime grant}); the perks themselves live in
 * the modules they belong to — Cosmetics, Ranks, Guilds, Economy's statues, Essentials' feature requests.
 */
public final class PrimeModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("prime", "Prime", "0.3.2")
            .describedAs("The Prime subscription: its overview, the store's commands, and map art for members")
            .by("Raindancer118");

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        SettingsStore<PrimeSettings> settings = context.settings(PrimeSettings.class, PrimeSettings.DEFAULTS);
        context.core().messages().defineFrom(PrimeModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(PrimeModule.class.getResourceAsStream("messages-serious.yml"));
        PermissionNodes.register(context.plugin().getServer());

        MapArtBook book = new MapArtBook(YamlStore.network(context.dataFolder().resolve("mapart.yml")));
        book.load();
        context.closeWith(book.watch());
        MapArtService mapArt = new MapArtService(context.plugin(), context.plugin().getServer(), book,
                context.core().messages(), settings::current, context.dataFolder().resolve("mapart"),
                context.core().staffReviews());
        context.closeWith(context.core().staffReviews().decider(MapArtReviews.KIND, mapArt::decide));
        context.closeWith(context.core().staffReviews().intake(MapArtReviews.KIND, submission -> mapArt.submit(
                submission, context.core().prime().isPrime(submission.player(), java.time.Instant.now()))));
        // Maps lose their custom drawing on a restart; the tiles are kept, so draw them again once the worlds are up.
        Scheduling.globalLater(context.plugin(), 1, () -> {
            int restored = mapArt.restore();
            if (restored > 0) {
                context.log().info("{} map art tile(s) drawn again.", restored);
            }
        });
        context.listener(new JoinListener(context.plugin(), mapArt));
        PrimeCommands.ready(new PrimeServices(context.core().messages(), context.core(), mapArt, settings::current));
    }

    @Override
    public List<ModuleCommand> commands() {
        return PrimeCommands.declared();
    }

    @Override
    public void disable() {
        PrimeCommands.stopped();
    }
}
