package de.raindancer.modules.prime.service;

import de.raindancer.core.moderation.review.StaffReviews;
import de.raindancer.core.platform.network.ThisServer;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.prime.PrimeSettings;
import de.raindancer.modules.prime.model.MapArtRequest;
import de.raindancer.modules.prime.rules.MapArtReviews;
import de.raindancer.modules.prime.rules.MapArtRule;
import de.raindancer.modules.prime.store.MapArtBook;
import de.raindancer.modules.prime.util.PermissionNodes;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Map art for Prime: a member asks for an image at a size, staff look at it and approve or refuse, the server draws it
 * on maps and hands them over. The tiles are kept as PNGs beside the plugin, so the maps are drawn again after every
 * restart; the maps themselves belong to the server they were drawn on.
 */
public final class MapArtService {

    private static final String ID_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789";
    /** Leaves room in Hush's 64 KiB request once base64 has grown it by a third. */
    private static final int PREVIEW_BYTES = 36 * 1024;
    /** The scaled picture as it was when asked for, beside the tiles. */
    private static final String KEPT = "asked.png";

    private final Plugin plugin;
    private final Server server;
    private final MapArtBook book;
    private final Messages messages;
    private final Supplier<PrimeSettings> settings;
    private final Path tiles;
    private final StaffReviews reviews;
    private final MapArtRule rule = new MapArtRule();
    private final ImageFetcher fetcher = new ImageFetcher();
    private final SecureRandom random = new SecureRandom();

    public MapArtService(Plugin plugin, Server server, MapArtBook book, Messages messages,
                         Supplier<PrimeSettings> settings, Path tiles, StaffReviews reviews) {
        this.plugin = plugin;
        this.server = server;
        this.book = book;
        this.messages = messages;
        this.settings = settings;
        this.tiles = tiles;
        this.reviews = reviews;
    }

    /** How many requests they have left this month. */
    public int left(Player player) {
        return left(player.getUniqueId());
    }

    private int left(java.util.UUID player) {
        return Math.max(0, settings.get().mapartPerMonth() - rule.usedThisMonth(book.all(), player, Instant.now()));
    }

    /** A request taken, or why not: the message key and its placeholders. */
    private record Opened(MapArtRequest request, String refusal, Object... args) {
    }

    private Opened open(java.util.UUID player, String name, String link, String sizeTyped, boolean prime) {
        PrimeSettings now = settings.get();
        if (!now.mapartEnabled()) {
            return new Opened(null, "prime.mapart.switched-off");
        }
        if (!prime) {
            return new Opened(null, "prime.mapart.not-prime");
        }
        Optional<MapArtRule.Size> size = rule.size(sizeTyped);
        if (size.isEmpty()) {
            return new Opened(null, "prime.mapart.usage");
        }
        if (!rule.fits(size.get(), now.mapartMostMaps())) {
            return new Opened(null, "prime.mapart.too-big", "most", now.mapartMostMaps());
        }
        Optional<String> wrong = rule.linkProblem(link);
        if (wrong.isPresent()) {
            return new Opened(null, "prime.mapart.bad-link", "reason", wrong.get());
        }
        if (left(player) <= 0) {
            return new Opened(null, "prime.mapart.month-used", "allowed", now.mapartPerMonth());
        }
        if (!book.readable()) {
            return new Opened(null, "prime.mapart.not-saved");
        }
        MapArtRequest request = new MapArtRequest(newId(), player, name, link.strip(), size.get().width(),
                size.get().height(), MapArtRequest.Status.WAITING, Instant.now(), "", "", "", List.of());
        if (!book.put(request)) {
            return new Opened(null, "prime.mapart.not-saved");
        }
        for (Player staff : server.getOnlinePlayers()) {
            if (staff.hasPermission(PermissionNodes.MAPART_REVIEW)) {
                announce(staff, request);
            }
        }
        Scheduling.async(plugin, () -> postWaiting(request));
        return new Opened(request, "");
    }

    public void ask(Player player, String link, String sizeTyped) {
        Opened opened = open(player.getUniqueId(), player.getName(), link, sizeTyped,
                player.hasPermission(PermissionNodes.MAPART));
        if (opened.request() == null) {
            messages.send(player, opened.refusal(), opened.args());
            return;
        }
        messages.send(player, "prime.mapart.asked", "id", opened.request().id(), "size", sizeOf(opened.request()));
    }

    /**
     * A request sent from outside the game (an image uploaded on Discord) for the sender's linked account. Prime is
     * read from Core's records, since the player may be offline. Any thread.
     */
    public StaffReviews.Answer submit(StaffReviews.Submission submission, boolean prime) {
        Opened opened = open(submission.player(), submission.name(), submission.link(), submission.options(), prime);
        if (opened.request() == null) {
            String refusal = opened.refusal().substring(opened.refusal().lastIndexOf('.') + 1);
            return new StaffReviews.Answer(refusal.replace('-', '_'), "", MapArtReviews.refusalText(refusal, opened.args()));
        }
        Player online = server.getPlayer(submission.player());
        if (online != null) {
            messages.send(online, "prime.mapart.asked", "id", opened.request().id(), "size", sizeOf(opened.request()));
        }
        return new StaffReviews.Answer("asked", opened.request().id(), String.format(Locale.ROOT,
                "Asked! Map art %s (%s maps) waits for the team. Once it is approved, the maps come to you in game.",
                opened.request().id(), sizeOf(opened.request())));
    }

    /** Off the server's threads: the request with a small picture of it, for staff on Discord. */
    private void postWaiting(MapArtRequest request) {
        byte[] preview = new byte[0];
        String problem = "";
        try {
            BufferedImage scaled = fetcher.scale(fetcher.fetch(request.url(),
                    settings.get().mapartMostMegabytes() * 1024L * 1024L), request.width(), request.height());
            // links from Discord uploads run out after a day: the picture staff approve is the one kept here
            Path folder = tiles.resolve(request.id());
            Files.createDirectories(folder);
            ImageIO.write(scaled, "png", folder.resolve(KEPT).toFile());
            preview = PreviewMaker.jpeg(scaled, PREVIEW_BYTES);
        } catch (IOException | RuntimeException failed) {
            problem = String.valueOf(failed.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        reviews.post(MapArtReviews.waiting(request, preview, problem));
    }

    /**
     * A decision made outside the game (Core's {@link StaffReviews}, e.g. Discord). Any thread. The console gets the
     * replies a staff member would; reviewers online are told who decided.
     */
    public StaffReviews.Outcome decide(String id, boolean approve, String by, String reason) {
        Optional<MapArtRequest> found = book.get(id);
        if (found.isEmpty()) {
            return StaffReviews.Outcome.UNKNOWN;
        }
        if (found.get().status() != MapArtRequest.Status.WAITING) {
            return StaffReviews.Outcome.ALREADY_DECIDED;
        }
        String who = (by == null || by.isBlank() ? "staff" : by.strip()) + " via Discord";
        for (Player staff : server.getOnlinePlayers()) {
            if (staff.hasPermission(PermissionNodes.MAPART_REVIEW)) {
                messages.send(staff, approve ? "prime.mapart.approved-elsewhere" : "prime.mapart.refused-elsewhere",
                        "id", found.get().id(), "player", found.get().name(), "by", who);
            }
        }
        if (approve) {
            approve(server.getConsoleSender(), who, found.get().id());
            return StaffReviews.Outcome.APPROVED;
        }
        refuse(server.getConsoleSender(), who, found.get().id(), reason);
        return StaffReviews.Outcome.REFUSED;
    }

    private void announce(CommandSender staff, MapArtRequest request) {
        net.kyori.adventure.text.Component open = messages.get("prime.mapart.open-image")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(request.url()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text(request.url())));
        messages.send(staff, "prime.mapart.staff-new", "player", request.name(), "size", sizeOf(request),
                "open", open, "id", request.id());
    }

    public void list(CommandSender staff) {
        List<MapArtRequest> waiting = book.all().stream()
                .filter(request -> request.status() == MapArtRequest.Status.WAITING).toList();
        if (waiting.isEmpty()) {
            messages.send(staff, "prime.mapart.none-waiting");
            return;
        }
        waiting.forEach(request -> announce(staff, request));
    }

    public void refuse(CommandSender staff, String id, String reason) {
        refuse(staff, staff.getName(), id, reason);
    }

    private void refuse(CommandSender staff, String by, String id, String reason) {
        Optional<MapArtRequest> found = waiting(staff, id);
        if (found.isEmpty()) {
            return;
        }
        String why = reason == null || reason.isBlank() ? "-" : reason.strip();
        if (!book.change(found.get().id(), request -> request.refused(by, why))) {
            messages.send(staff, "prime.mapart.not-saved");
            return;
        }
        reviews.post(MapArtReviews.refused(found.get(), by, why));
        messages.send(staff, "prime.mapart.refused-staff", "id", found.get().id(), "player", found.get().name());
        Player asker = server.getPlayer(found.get().player());
        if (asker != null) {
            messages.send(asker, "prime.mapart.refused", "id", found.get().id(), "reason", why);
        }
    }

    /** Fetches and draws it; the maps go to the player now if they are here, else when they next join here. */
    public void approve(CommandSender staff, String id) {
        approve(staff, staff.getName(), id);
    }

    private void approve(CommandSender staff, String by, String id) {
        Optional<MapArtRequest> found = waiting(staff, id);
        if (found.isEmpty()) {
            return;
        }
        MapArtRequest request = found.get();
        messages.send(staff, "prime.mapart.fetching", "id", request.id());
        reviews.post(MapArtReviews.approved(request, by));
        long mostBytes = settings.get().mapartMostMegabytes() * 1024L * 1024L;
        Scheduling.async(plugin, () -> {
            List<BufferedImage> parts = new ArrayList<>();
            try {
                Path folder = tiles.resolve(request.id());
                Path kept = folder.resolve(KEPT);
                BufferedImage scaled = Files.exists(kept) ? ImageIO.read(kept.toFile())
                        : fetcher.scale(fetcher.fetch(request.url(), mostBytes), request.width(), request.height());
                Files.createDirectories(folder);
                for (int index = 0; index < request.tiles(); index++) {
                    BufferedImage part = fetcher.tile(scaled, index, request.width());
                    ImageIO.write(part, "png", folder.resolve(index + ".png").toFile());
                    parts.add(part);
                }
            } catch (IOException | RuntimeException failed) {
                messages.send(staff, "prime.mapart.failed", "id", request.id(), "reason", String.valueOf(failed.getMessage()));
                reviews.post(MapArtReviews.failed(request, by, String.valueOf(failed.getMessage())));
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            Scheduling.global(plugin, () -> draw(staff, by, request, parts));
        });
    }

    private void draw(CommandSender staff, String by, MapArtRequest request, List<BufferedImage> parts) {
        World world = server.getWorlds().getFirst();
        List<Integer> ids = new ArrayList<>();
        for (BufferedImage part : parts) {
            MapView map = server.createMap(world);
            dress(map, part);
            ids.add(map.getId());
        }
        if (!book.change(request.id(), current -> current.drawn(by, ThisServer.id(), ids))) {
            messages.send(staff, "prime.mapart.not-saved");
            reviews.post(MapArtReviews.failed(request, by, "the drawn maps could not be saved"));
            return;
        }
        reviews.post(MapArtReviews.drawn(request, by));
        messages.send(staff, "prime.mapart.approved-staff", "id", request.id(), "player", request.name());
        Player asker = server.getPlayer(request.player());
        if (asker != null) {
            Scheduling.entity(plugin, asker, () -> deliver(asker));
        }
    }

    private static void dress(MapView map, BufferedImage part) {
        new ArrayList<>(map.getRenderers()).forEach(map::removeRenderer);
        map.setScale(MapView.Scale.CLOSEST);
        map.setTrackingPosition(false);
        map.setUnlimitedTracking(false);
        map.setLocked(true);
        map.addRenderer(new TileRenderer(part));
    }

    /** On the player's own thread: everything drawn for them on this server and not handed over yet. */
    public void deliver(Player player) {
        for (MapArtRequest request : book.all()) {
            if (!request.player().equals(player.getUniqueId()) || request.status() != MapArtRequest.Status.DRAWN
                    || !request.server().equals(ThisServer.id())) {
                continue;
            }
            if (!book.change(request.id(), MapArtRequest::delivered)) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            for (int index = 0; index < request.maps().size(); index++) {
                MapView map = server.getMap(request.maps().get(index));
                if (map == null) {
                    continue;
                }
                ItemStack item = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) item.getItemMeta();
                meta.setMapView(map);
                meta.itemName(messages.get("prime.mapart.item", "id", request.id(),
                        "column", index % request.width() + 1, "row", index / request.width() + 1));
                item.setItemMeta(meta);
                items.add(item);
            }
            boolean dropped = !player.getInventory().addItem(items.toArray(ItemStack[]::new)).values().stream()
                    .peek(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)).toList().isEmpty();
            messages.send(player, dropped ? "prime.mapart.delivered-dropped" : "prime.mapart.delivered",
                    "id", request.id(), "count", items.size(), "size", sizeOf(request));
        }
    }

    /** After a restart: every map drawn here gets its picture back. On the global thread. @return how many */
    public int restore() {
        int restored = 0;
        for (MapArtRequest request : book.all()) {
            if (request.maps().isEmpty() || !request.server().equals(ThisServer.id())) {
                continue;
            }
            for (int index = 0; index < request.maps().size(); index++) {
                MapView map = server.getMap(request.maps().get(index));
                Path file = tiles.resolve(request.id()).resolve(index + ".png");
                if (map == null || !Files.exists(file)) {
                    continue;
                }
                try {
                    dress(map, ImageIO.read(file.toFile()));
                    restored++;
                } catch (IOException unreadable) {
                    plugin.getSLF4JLogger().warn("Map art {} tile {} could not be read: {}", request.id(), index,
                            unreadable.getMessage());
                }
            }
        }
        return restored;
    }

    private Optional<MapArtRequest> waiting(CommandSender staff, String id) {
        Optional<MapArtRequest> found = book.get(id);
        if (found.isEmpty()) {
            messages.send(staff, "prime.mapart.unknown", "id", String.valueOf(id));
            return Optional.empty();
        }
        if (found.get().status() != MapArtRequest.Status.WAITING) {
            messages.send(staff, "prime.mapart.not-waiting", "id", found.get().id());
            return Optional.empty();
        }
        return found;
    }

    private String newId() {
        String id;
        do {
            StringBuilder built = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                built.append(ID_LETTERS.charAt(random.nextInt(ID_LETTERS.length())));
            }
            id = built.toString();
        } while (book.get(id).isPresent());
        return id;
    }

    private static String sizeOf(MapArtRequest request) {
        return String.format(Locale.ROOT, "%d×%d", request.width(), request.height());
    }
}
