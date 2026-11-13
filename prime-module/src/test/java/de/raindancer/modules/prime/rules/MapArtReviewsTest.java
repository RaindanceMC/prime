package de.raindancer.modules.prime.rules;

import de.raindancer.core.moderation.review.StaffReviews;
import de.raindancer.modules.prime.model.MapArtRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapArtReviewsTest {

    private final UUID ada = UUID.randomUUID();
    private final MapArtRequest request = new MapArtRequest("k3x9q", ada, "Ada", "https://example.com/cat.png", 3, 2,
            MapArtRequest.Status.WAITING, Instant.parse("2026-11-13T12:00:00Z"), "", "", "", List.of());

    @Test
    @DisplayName("a waiting request becomes a mapart review with its size, link and preview")
    void waiting() {
        StaffReviews.Review review = MapArtReviews.waiting(request, new byte[]{9}, "");

        assertThat(review.kind()).isEqualTo(MapArtReviews.KIND);
        assertThat(review.id()).isEqualTo("k3x9q");
        assertThat(review.player()).isEqualTo(ada);
        assertThat(review.name()).isEqualTo("Ada");
        assertThat(review.title()).isEqualTo("Map art 3×2 (6 maps)");
        assertThat(review.link()).isEqualTo("https://example.com/cat.png");
        assertThat(review.preview()).containsExactly(9);
        assertThat(review.previewType()).isEqualTo("image/jpeg");
        assertThat(review.state()).isEqualTo(StaffReviews.State.WAITING);
    }

    @Test
    @DisplayName("without a preview it says why, and names no picture type")
    void noPreview() {
        StaffReviews.Review review = MapArtReviews.waiting(request, new byte[0], "the link answered 404");
        assertThat(review.previewType()).isEmpty();
        assertThat(review.detail()).isEqualTo("The server could not load the image: the link answered 404");
    }

    @Test
    @DisplayName("a decision made in game or on Discord ends up as the same review states")
    void decisions() {
        assertThat(MapArtReviews.refused(request, "Mo", "blurry").state()).isEqualTo(StaffReviews.State.REFUSED);
        assertThat(MapArtReviews.approved(request, "Mo").state()).isEqualTo(StaffReviews.State.APPROVED);
        StaffReviews.Review done = MapArtReviews.drawn(request, "Mo");
        assertThat(done.state()).isEqualTo(StaffReviews.State.DONE);
        assertThat(done.detail()).isEqualTo("6 maps drawn. Ada gets them now, or when they next join.");
        assertThat(MapArtReviews.failed(request, "Mo", "404").reason()).isEqualTo("404");
    }

    @Test
    @DisplayName("a request from Discord that is not taken says why in one sentence, with its number")
    void refusals() {
        assertThat(MapArtReviews.refusalText("too-big", "most", 16)).isEqualTo("One image covers at most 16 maps.");
        assertThat(MapArtReviews.refusalText("month-used", "allowed", 1)).contains("(1 a month)");
        assertThat(MapArtReviews.refusalText("bad-link", "reason", "only https:// links"))
                .isEqualTo("That image cannot be used: only https:// links.");
        assertThat(MapArtReviews.refusalText("not-prime")).contains("Prime perk");
        assertThat(MapArtReviews.refusalText("anything else")).contains("Try again");
    }
}
