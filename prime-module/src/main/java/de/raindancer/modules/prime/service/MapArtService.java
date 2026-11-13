package de.raindancer.modules.prime.service;

import de.raindancer.core.platform.network.ThisServer;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.prime.PrimeSettings;
import de.raindancer.modules.prime.model.MapArtRequest;
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

    private final Plugin plugin;
    private final Server server;
    private final MapArtBook book;
    private final Messages messages;
    private final Supplier<PrimeSettings> settings;
    private final Path tiles;
    private final MapArtRule rule = new MapArtRule();
    private final ImageFetcher fetcher = new ImageFetcher();
    private final SecureRandom random = new SecureRandom();

    public MapArtService(Plugin plugin, Server server, MapArtBook book, Messages messages,
                         Supplier<PrimeSettings> settings, Path tiles) {
        this.plugin = plugin;
        this.server = server;
        this.book = book;
        this.messages = messages;
        this.settings = settings;
        this.tiles = tiles;
    }

    /** How many requests they have left this month. */
    public int left(Player player) {
        return Math.max(0, settings.get().mapartPerMonth()
                - rule.usedThisMonth(book.all(), player.getUniqueId(), Instant.now()));
    }

    public void ask(Player player, String link, String sizeTyped) {
        PrimeSettings now = settings.get();
        if (!now.mapartEnabled()) {
            messages.send(player, "prime.mapart.switched-off");
            return;
        }
        if (!player.hasPermission(PermissionNodes.MAPART)) {
            messages.send(player, "prime.mapart.not-prime");
            return;
        }
        Optional<MapArtRule.Size> size = rule.size(sizeTyped);
        if (size.isEmpty()) {
            messages.send(player, "prime.mapart.usage");
            return;
        }
        if (!rule.fits(size.get(), now.mapartMostMaps())) {
            messages.send(player, "prime.mapart.too-big", "most", now.mapartMostMaps());
            return;
        }
        Optional<String> wrong = rule.linkProblem(link);
        if (wrong.isPresent()) {
            messages.send(player, "prime.mapart.bad-link", "reason", wrong.get());
            return;
        }
        if (left(player) <= 0) {
            messages.send(player, "prime.mapart.month-used", "allowed", now.mapartPerMonth());
            return;
        }
        if (!book.readable()) {
            messages.send(player, "prime.mapart.not-saved");
            return;
        }
        MapArtRequest request = new MapArtRequest(newId(), player.getUniqueId(), player.getName(), link.strip(),
                size.get().width(), size.get().height(), MapArtRequest.Status.WAITING, Instant.now(), "", "", "",
                List.of());
        if (!book.put(request)) {
            messages.send(player, "prime.mapart.not-saved");
            return;
        }
        messages.send(player, "prime.mapart.asked", "id", request.id(), "size", sizeOf(request));
        for (Player staff : server.getOnlinePlayers()) {
            if (staff.hasPermission(PermissionNodes.MAPART_REVIEW)) {
                announce(staff, request);
            }
        }
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
        Optional<MapArtRequest> found = waiting(staff, id);
        if (found.isEmpty()) {
            return;
        }
        String why = reason == null || reason.isBlank() ? "-" : reason.strip();
        if (!book.change(found.get().id(), request -> request.refused(staff.getName(), why))) {
            messages.send(staff, "prime.mapart.not-saved");
            return;
        }
        messages.send(staff, "prime.mapart.refused-staff", "id", found.get().id(), "player", found.get().name());
        Player asker = server.getPlayer(found.get().player());
        if (asker != null) {
            messages.send(asker, "prime.mapart.refused", "id", found.get().id(), "reason", why);
        }
    }

    /** Fetches and draws it; the maps go to the player now if they are here, else when they next join here. */
    public void approve(CommandSender staff, String id) {
        Optional<MapArtRequest> found = waiting(staff, id);
        if (found.isEmpty()) {
            return;
        }
        MapArtRequest request = found.get();
        messages.send(staff, "prime.mapart.fetching", "id", request.id());
        long mostBytes = settings.get().mapartMostMegabytes() * 1024L * 1024L;
        Scheduling.async(plugin, () -> {
            List<BufferedImage> parts = new ArrayList<>();
            try {
                BufferedImage scaled = fetcher.scale(fetcher.fetch(request.url(), mostBytes), request.width(),
                        request.height());
                Path folder = tiles.resolve(request.id());
                Files.createDirectories(folder);
                for (int index = 0; index < request.tiles(); index++) {
                    BufferedImage part = fetcher.tile(scaled, index, request.width());
                    ImageIO.write(part, "png", folder.resolve(index + ".png").toFile());
                    parts.add(part);
                }
            } catch (IOException | RuntimeException failed) {
                messages.send(staff, "prime.mapart.failed", "id", request.id(), "reason", String.valueOf(failed.getMessage()));
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            Scheduling.global(plugin, () -> draw(staff, request, parts));
        });
    }

    private void draw(CommandSender staff, MapArtRequest request, List<BufferedImage> parts) {
        World world = server.getWorlds().getFirst();
        List<Integer> ids = new ArrayList<>();
        for (BufferedImage part : parts) {
            MapView map = server.createMap(world);
            dress(map, part);
            ids.add(map.getId());
        }
        if (!book.change(request.id(), current -> current.drawn(staff.getName(), ThisServer.id(), ids))) {
            messages.send(staff, "prime.mapart.not-saved");
            return;
        }
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
