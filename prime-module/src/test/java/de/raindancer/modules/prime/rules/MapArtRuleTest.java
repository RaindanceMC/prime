package de.raindancer.modules.prime.rules;

import de.raindancer.modules.prime.model.MapArtRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapArtRuleTest {

    private final MapArtRule rule = new MapArtRule();
    private final UUID ada = UUID.randomUUID();

    private MapArtRequest asked(UUID who, String when, MapArtRequest.Status status) {
        return new MapArtRequest("x" + when.hashCode(), who, "Ada", "https://a.b/c.png", 2, 2, status,
                Instant.parse(when), "", "", "", List.of());
    }

    @Test
    @DisplayName("a size is maps wide by maps high: 3x2, 3×2, 3 x 2; nothing below one")
    void sizes() {
        assertThat(rule.size("3x2")).contains(new MapArtRule.Size(3, 2));
        assertThat(rule.size(" 3 × 2 ")).contains(new MapArtRule.Size(3, 2));
        assertThat(rule.size("0x2")).isEmpty();
        assertThat(rule.size("3")).isEmpty();
        assertThat(rule.size("100x1")).isEmpty();
        assertThat(rule.fits(new MapArtRule.Size(4, 4), 16)).isTrue();
        assertThat(rule.fits(new MapArtRule.Size(5, 4), 16)).isFalse();
    }

    @Test
    @DisplayName("this month's requests count, refused ones and last month's do not")
    void quota() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        List<MapArtRequest> all = List.of(asked(ada, "2026-11-02T10:00:00Z", MapArtRequest.Status.DELIVERED),
                asked(ada, "2026-11-03T10:00:00Z", MapArtRequest.Status.REFUSED),
                asked(ada, "2026-10-30T10:00:00Z", MapArtRequest.Status.DELIVERED),
                asked(UUID.randomUUID(), "2026-11-04T10:00:00Z", MapArtRequest.Status.WAITING));
        assertThat(rule.usedThisMonth(all, ada, now)).isEqualTo(1);
    }

    @Test
    @DisplayName("links: https only, a host, no credentials, the normal port")
    void links() {
        assertThat(rule.linkProblem("https://i.imgur.com/cat.png")).isEmpty();
        assertThat(rule.linkProblem("http://i.imgur.com/cat.png")).isPresent();
        assertThat(rule.linkProblem("file:///etc/passwd")).isPresent();
        assertThat(rule.linkProblem("https://user:pw@host.com/a.png")).isPresent();
        assertThat(rule.linkProblem("https://host.com:8443/a.png")).isPresent();
        assertThat(rule.linkProblem("not a link at all")).isPresent();
    }

    @Test
    @DisplayName("only the public internet: no loopback, private, link-local, CGNAT, unique-local or mapped private address")
    void addresses() throws Exception {
        for (String inside : List.of("127.0.0.1", "10.20.0.11", "192.168.1.1", "172.16.5.5", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1", "::ffff:10.0.0.1")) {
            assertThat(rule.isPublic(InetAddress.getByName(inside))).as(inside).isFalse();
        }
        for (String outside : List.of("1.1.1.1", "65.21.15.243", "2606:4700:4700::1111")) {
            assertThat(rule.isPublic(InetAddress.getByName(outside))).as(outside).isTrue();
        }
    }

    @Test
    @DisplayName("a picture is cropped evenly to the shape of the maps, never stretched; tiles go row by row")
    void cropAndTiles() {
        assertThat(rule.cover(1000, 500, 256, 256)).containsExactly(250, 0, 500, 500);
        assertThat(rule.cover(500, 1000, 256, 128)).containsExactly(0, 375, 500, 250);
        assertThat(rule.tile(0, 3)).containsExactly(0, 0, 128, 128);
        assertThat(rule.tile(4, 3)).containsExactly(128, 128, 128, 128);
    }
}
