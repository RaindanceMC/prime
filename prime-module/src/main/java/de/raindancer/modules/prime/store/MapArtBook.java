package de.raindancer.modules.prime.store;

import de.raindancer.core.data.store.KeyedDocument;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.modules.prime.model.MapArtRequest;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Every map art request, {@code mapart.yml}, one per key, for the whole network. */
public final class MapArtBook {

    private final KeyedDocument<MapArtRequest> document;

    public MapArtBook(YamlStore store) {
        this.document = new KeyedDocument<>(store, "requests", MapArtBook::read, MapArtBook::write);
    }

    private static MapArtRequest read(ConfigurationSection yaml, String key) {
        ConfigurationSection each = yaml.getConfigurationSection(key);
        if (each == null) {
            return null;
        }
        try {
            return new MapArtRequest(key, UUID.fromString(each.getString("player", "")), each.getString("name", "?"),
                    each.getString("url", ""), each.getInt("width", 1), each.getInt("height", 1),
                    MapArtRequest.Status.valueOf(each.getString("status", "WAITING")),
                    Instant.parse(String.valueOf(each.get("asked"))), each.getString("decider", ""),
                    each.getString("reason", ""), each.getString("server", ""), each.getIntegerList("maps"));
        } catch (RuntimeException handEdited) {
            return null;
        }
    }

    private static void write(ConfigurationSection yaml, String path, MapArtRequest request) {
        yaml.set(path + ".player", request.player().toString());
        yaml.set(path + ".name", request.name());
        yaml.set(path + ".url", request.url());
        yaml.set(path + ".width", request.width());
        yaml.set(path + ".height", request.height());
        yaml.set(path + ".status", request.status().name());
        yaml.set(path + ".asked", request.asked().toString());
        yaml.set(path + ".decider", request.decider().isEmpty() ? null : request.decider());
        yaml.set(path + ".reason", request.reason().isEmpty() ? null : request.reason());
        yaml.set(path + ".server", request.server().isEmpty() ? null : request.server());
        yaml.set(path + ".maps", request.maps().isEmpty() ? null : request.maps());
    }

    public void load() {
        document.load();
    }

    public NetworkBus.Subscription watch() {
        return document.onChange(changed -> { });
    }

    public boolean readable() {
        return document.readable();
    }

    public Optional<MapArtRequest> get(String id) {
        return id == null ? Optional.empty() : document.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    /** Oldest first. */
    public List<MapArtRequest> all() {
        return document.all().values().stream().sorted(Comparator.comparing(MapArtRequest::asked)).toList();
    }

    public boolean put(MapArtRequest request) {
        return document.change(request.id(), current -> request);
    }

    public boolean change(String id, UnaryOperator<MapArtRequest> change) {
        return document.change(id, current -> current == null ? null : change.apply(current));
    }
}
