package de.raindancer.modules.prime.command;

import de.raindancer.core.social.support.PrimeMembers;
import de.raindancer.modules.prime.PrimeServices;
import de.raindancer.modules.prime.util.PermissionNodes;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /prime}: what Prime is and gives, and the player's own; {@code mapart …}; the store's and staff's
 * {@code grant|revoke|info} are Core's {@link de.raindancer.core.social.support.PrimeCommand}.
 */
public final class PrimeCommand implements BasicCommand {

    private static final List<String> CORE_WORDS = List.of("grant", "revoke", "info");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            .withZone(ZoneId.of("Europe/Berlin"));

    private final Supplier<PrimeServices> services;
    private final de.raindancer.core.social.support.PrimeCommand core =
            new de.raindancer.core.social.support.PrimeCommand(source -> { });

    public PrimeCommand(Supplier<PrimeServices> services) {
        this.services = services;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        PrimeServices live = services.get();
        CommandSender sender = source.getSender();
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (CORE_WORDS.contains(first) && sender.hasPermission(de.raindancer.core.social.support.PrimeCommand.ADMIN)) {
            core.execute(source, args);
            return;
        }
        if (first.equals("mapart")) {
            mapArt(live, sender, Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        overview(live, sender);
    }

    private static void overview(PrimeServices live, CommandSender sender) {
        live.messages().send(sender, "prime.overview.header");
        Optional<PrimeMembers.Member> member = sender instanceof Player player
                ? live.core().prime().of(player.getUniqueId()) : Optional.empty();
        boolean active = member.isPresent() && member.get().activeAt(Instant.now());
        if (active) {
            live.messages().send(sender, "prime.overview.active", "until", DAY.format(member.get().until()),
                    "since", DAY.format(member.get().since()), "months", member.get().months().size());
        }
        for (String perk : List.of("prime.overview.perk-names", "prime.overview.perk-skins", "prime.overview.perk-drops",
                "prime.overview.perk-badge", "prime.overview.perk-guild", "prime.overview.perk-statue",
                "prime.overview.perk-mapart", "prime.overview.perk-ideas", "prime.overview.perk-role")) {
            live.messages().send(sender, perk);
        }
        if (active && sender instanceof Player player && live.settings().get().mapartEnabled()) {
            live.messages().send(sender, "prime.overview.mapart-left", "left", live.mapArt().left(player));
        }
        String store = live.settings().get().storeUrl();
        if (!active && !store.isEmpty()) {
            net.kyori.adventure.text.Component button = live.messages().get("prime.overview.store-button")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(store))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(live.messages().get("prime.overview.store-hover")));
            live.messages().send(sender, "prime.overview.store", "button", button, "url", store);
        } else if (!active) {
            live.messages().send(sender, "prime.overview.not-prime");
        }
    }

    private static void mapArt(PrimeServices live, CommandSender sender, String[] args) {
        String word = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean staff = sender.hasPermission(PermissionNodes.MAPART_REVIEW);
        switch (word) {
            case "list" -> {
                if (staff) {
                    live.mapArt().list(sender);
                } else {
                    live.messages().send(sender, "prime.mapart.usage");
                }
            }
            case "approve" -> {
                if (staff && args.length >= 2) {
                    live.mapArt().approve(sender, args[1]);
                } else {
                    live.messages().send(sender, staff ? "prime.mapart.usage-staff" : "prime.mapart.usage");
                }
            }
            case "refuse", "deny" -> {
                if (staff && args.length >= 2) {
                    live.mapArt().refuse(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                } else {
                    live.messages().send(sender, staff ? "prime.mapart.usage-staff" : "prime.mapart.usage");
                }
            }
            default -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    live.messages().send(sender, "prime.mapart.usage");
                    return;
                }
                live.mapArt().ask(player, args[0], args[1]);
            }
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options;
        if (args.length <= 1) {
            options = new java.util.ArrayList<>(List.of("mapart"));
            if (sender.hasPermission(de.raindancer.core.social.support.PrimeCommand.ADMIN)) {
                options.addAll(CORE_WORDS);
            }
        } else if (args[0].equalsIgnoreCase("mapart") && args.length == 2) {
            options = new java.util.ArrayList<>(List.of("https://"));
            if (sender.hasPermission(PermissionNodes.MAPART_REVIEW)) {
                options.addAll(List.of("list", "approve", "refuse"));
            }
        } else if (args[0].equalsIgnoreCase("mapart") && args.length == 3 && args[1].startsWith("http")) {
            options = List.of("1x1", "2x2", "3x2", "4x4");
        } else if (CORE_WORDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return core.suggest(source, args);
        } else {
            return List.of();
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
