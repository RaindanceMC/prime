package de.raindancer.modules.prime.store;

import de.raindancer.core.data.store.Documents;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.prime.model.MapArtRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapArtBookTest {

    @TempDir
    Path folder;

    private MapArtBook fresh() {
        MapArtBook book = new MapArtBook(new YamlStore(folder.resolve("mapart.yml"), null, Documents.Scope.NETWORK));
        book.load();
        return book;
    }

    @Test
    @DisplayName("a request goes from waiting to drawn to delivered, and reads back the same on another server")
    void lifecycle() {
        MapArtBook book = fresh();
        MapArtRequest asked = new MapArtRequest("abcde", UUID.randomUUID(), "Ada", "https://a.b/c.png", 2, 1,
                MapArtRequest.Status.WAITING, Instant.parse("2026-11-13T12:00:00Z"), "", "", "", List.of());
        assertThat(book.put(asked)).isTrue();
        assertThat(book.change("abcde", request -> request.drawn("Tom", "hyades_smp", List.of(7, 8)))).isTrue();

        MapArtRequest read = fresh().get("abcde").orElseThrow();
        assertThat(read.status()).isEqualTo(MapArtRequest.Status.DRAWN);
        assertThat(read.maps()).containsExactly(7, 8);
        assertThat(read.server()).isEqualTo("hyades_smp");
        assertThat(read.decider()).isEqualTo("Tom");

        book.change("abcde", MapArtRequest::delivered);
        assertThat(fresh().get("ABCDE").orElseThrow().status()).isEqualTo(MapArtRequest.Status.DELIVERED);
    }
}
