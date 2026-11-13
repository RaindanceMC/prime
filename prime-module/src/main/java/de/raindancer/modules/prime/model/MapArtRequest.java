package de.raindancer.modules.prime.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One Prime member's wish for an image as map art, from asking to having the maps.
 *
 * @param maps    the map ids it was drawn on, row by row — empty until approved
 * @param server  the server those maps belong to (map ids are each world's own)
 * @param decider who approved or refused it; empty while it waits
 */
public record MapArtRequest(String id, UUID player, String name, String url, int width, int height, Status status,
                            Instant asked, String decider, String reason, String server, List<Integer> maps) {

    public enum Status {
        /** Waiting for staff. */
        WAITING,
        /** Approved and drawn; the maps wait for the player to come to the server they were drawn on. */
        DRAWN,
        /** The player has the maps. */
        DELIVERED,
        /** Refused, with a reason. Does not count against the month. */
        REFUSED
    }

    public MapArtRequest {
        maps = maps == null ? List.of() : List.copyOf(maps);
        decider = decider == null ? "" : decider;
        reason = reason == null ? "" : reason;
        server = server == null ? "" : server;
    }

    public int tiles() {
        return width * height;
    }

    public MapArtRequest refused(String by, String why) {
        return new MapArtRequest(id, player, name, url, width, height, Status.REFUSED, asked, by, why, server, maps);
    }

    public MapArtRequest drawn(String by, String on, List<Integer> mapIds) {
        return new MapArtRequest(id, player, name, url, width, height, Status.DRAWN, asked, by, reason, on, mapIds);
    }

    public MapArtRequest delivered() {
        return new MapArtRequest(id, player, name, url, width, height, Status.DELIVERED, asked, decider, reason, server, maps);
    }
}
