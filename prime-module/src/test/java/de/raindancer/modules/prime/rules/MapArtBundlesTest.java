package de.raindancer.modules.prime.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MapArtBundlesTest {

    private static List<Integer> range(int from, int to) {
        return IntStream.range(from, to).boxed().toList();
    }

    @Test
    @DisplayName("a whole map art goes into one bundle, in tile order")
    void oneBundle() {
        assertThat(MapArtBundles.pack(4)).containsExactly(range(0, 4));
        assertThat(MapArtBundles.pack(16)).containsExactly(range(0, 16));
    }

    @Test
    @DisplayName("a bundle is full at 64 maps; the largest allowed map art still fits in one")
    void fullBundle() {
        assertThat(MapArtBundles.pack(64)).containsExactly(range(0, 64));
        assertThat(MapArtBundles.pack(65)).containsExactly(range(0, 64), List.of(64));
    }

    @Test
    @DisplayName("no maps, no bundle")
    void nothing() {
        assertThat(MapArtBundles.pack(0)).isEmpty();
    }
}
