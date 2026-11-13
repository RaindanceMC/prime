package de.raindancer.modules.prime.rules;

import de.raindancer.core.moderation.review.StaffReviews;
import de.raindancer.modules.prime.model.MapArtRequest;

import java.util.Locale;

/** A map art request as Core's staff review, so it can be shown and decided outside the game (Discord). */
public final class MapArtReviews {

    public static final String KIND = "mapart";

    private MapArtReviews() {
    }

    public static StaffReviews.Review waiting(MapArtRequest request, byte[] preview, String loadProblem) {
        boolean hasPreview = preview != null && preview.length > 0;
        StaffReviews.Review review = StaffReviews.Review.waiting(KIND, request.id(), request.player(), request.name(),
                title(request), request.url(), hasPreview ? preview : new byte[0], hasPreview ? "image/jpeg" : "");
        return loadProblem == null || loadProblem.isBlank() ? review
                : review.withDetail("The server could not load the image: " + loadProblem);
    }

    public static StaffReviews.Review approved(MapArtRequest request, String by) {
        return base(request).approved(by);
    }

    public static StaffReviews.Review refused(MapArtRequest request, String by, String why) {
        return base(request).refused(by, why);
    }

    public static StaffReviews.Review drawn(MapArtRequest request, String by) {
        return base(request).done(by, request.tiles() + " maps drawn. " + request.name()
                + " gets them now, or when they next join.");
    }

    public static StaffReviews.Review failed(MapArtRequest request, String by, String why) {
        return base(request).failed(by, why);
    }

    private static StaffReviews.Review base(MapArtRequest request) {
        return StaffReviews.Review.waiting(KIND, request.id(), request.player(), request.name(), title(request),
                request.url(), null, null);
    }

    static String title(MapArtRequest request) {
        return String.format(Locale.ROOT, "Map art %d×%d (%d map%s)", request.width(), request.height(),
                request.tiles(), request.tiles() == 1 ? "" : "s");
    }
}
