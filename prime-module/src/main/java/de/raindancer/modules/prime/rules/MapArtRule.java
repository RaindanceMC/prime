package de.raindancer.modules.prime.rules;

import de.raindancer.modules.prime.model.MapArtRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The decisions about map art, apart from the server so they can be tested as plain values. */
public final class MapArtRule {

    /** One map is 128 pixels square. */
    public static final int MAP_PIXELS = 128;
    private static final Pattern SIZE = Pattern.compile("^(\\d{1,2})\\s*[x×*]\\s*(\\d{1,2})$");

    public record Size(int width, int height) {
    }

    /** {@code 3x2} — maps wide by maps high. */
    public Optional<Size> size(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        Matcher matcher = SIZE.matcher(typed.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int width = Integer.parseInt(matcher.group(1));
        int height = Integer.parseInt(matcher.group(2));
        return width < 1 || height < 1 ? Optional.empty() : Optional.of(new Size(width, height));
    }

    public boolean fits(Size size, int mostMaps) {
        return size.width() * size.height() <= mostMaps;
    }

    /** Sizes a player can ask for by name; the server picks the maps from the picture's shape. */
    public enum Preset {
        SMALL(8), MEDIUM(24), LARGE(Integer.MAX_VALUE);

        /** The most maps this preset covers, before the server's own limit. */
        final int maps;

        Preset(int maps) {
            this.maps = maps;
        }
    }

    /** How far a size's shape may be off the picture's, as a log of the ratios; beyond it a fifth gets cut off. */
    private static final double FAR_OFF = 0.25;

    public Optional<Preset> preset(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        return switch (typed.strip().toLowerCase(Locale.ROOT)) {
            case "small" -> Optional.of(Preset.SMALL);
            case "medium" -> Optional.of(Preset.MEDIUM);
            case "large" -> Optional.of(Preset.LARGE);
            default -> Optional.empty();
        };
    }

    /**
     * The size for a picture of {@code pixelsWide × pixelsHigh}: as many maps as the preset and the server's limit
     * allow, less for every bit the shape is off the picture's, since that part is cut away. A shape nothing comes
     * close to (a banner fifty times wider than high) gets the closest one there is.
     */
    public Size fit(int pixelsWide, int pixelsHigh, Preset preset, int mostMaps) {
        int budget = Math.max(1, Math.min(preset.maps, mostMaps));
        double ratio = Math.max(1, pixelsWide) / (double) Math.max(1, pixelsHigh);
        Size best = null;
        double bestScore = -1;
        Size closest = new Size(1, 1);
        double closestOff = Double.MAX_VALUE;
        for (int height = 1; height <= budget; height++) {
            for (int width = 1; width * height <= budget; width++) {
                double off = Math.abs(Math.log(width / (double) height / ratio));
                double score = width * height * (1 - 2 * off);
                if (off <= FAR_OFF && score > bestScore) {
                    best = new Size(width, height);
                    bestScore = score;
                }
                if (off < closestOff) {
                    closest = new Size(width, height);
                    closestOff = off;
                }
            }
        }
        return best != null ? best : closest;
    }

    /** Requests this month that count — refused ones do not. */
    public int usedThisMonth(Collection<MapArtRequest> all, UUID player, Instant now) {
        YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
        return (int) all.stream().filter(request -> request.player().equals(player))
                .filter(request -> request.status() != MapArtRequest.Status.REFUSED)
                .filter(request -> YearMonth.from(request.asked().atZone(ZoneOffset.UTC)).equals(month))
                .count();
    }

    /** Why a link cannot be fetched, or empty: https only, a real host name, no credentials, no odd port. */
    public Optional<String> linkProblem(String typed) {
        URI uri;
        try {
            uri = new URI(typed == null ? "" : typed.strip());
        } catch (Exception broken) {
            return Optional.of("not a link");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return Optional.of("only https:// links");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return Optional.of("no host");
        }
        if (uri.getUserInfo() != null) {
            return Optional.of("no user names or passwords in the link");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return Optional.of("only the normal https port");
        }
        if (uri.toString().length() > 500) {
            return Optional.of("too long");
        }
        return Optional.empty();
    }

    /**
     * Whether the server may fetch from this address: only the public internet, never itself, the network it sits in
     * or anything private — a player's link must not become a way into the servers next to this one.
     */
    public boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // 100.64/10 carrier-grade NAT, 0/8, 192.0.0/24, 198.18/15 benchmarking, 240/4 reserved
            return !(first == 0 || (first == 100 && second >= 64 && second <= 127) || (first == 192 && second == 0 && (bytes[2] & 0xff) == 0)
                    || (first == 198 && (second == 18 || second == 19)) || first >= 240);
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            // fc00::/7 unique local; ::ffff:a.b.c.d mapped addresses are judged as the IPv4 they carry
            if ((first & 0xfe) == 0xfc) {
                return false;
            }
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= bytes[i] == 0;
            }
            if (mapped && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]}));
                } catch (java.net.UnknownHostException impossible) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The part of the scaled image a map shows: {x, y, width, height} in pixels of an image {@code width × height} maps
     * large, maps counted row by row from the top left.
     */
    public int[] tile(int index, int width) {
        return new int[]{(index % width) * MAP_PIXELS, (index / width) * MAP_PIXELS, MAP_PIXELS, MAP_PIXELS};
    }

    /**
     * How to fill {@code targetW × targetH} with an image of {@code w × h} without stretching it: scaled until it
     * covers the whole area, the overhang cut evenly off both sides. {x, y, width, height} of the source to take.
     */
    public int[] cover(int w, int h, int targetW, int targetH) {
        double scale = Math.max(targetW / (double) w, targetH / (double) h);
        int takeW = (int) Math.min(w, Math.round(targetW / scale));
        int takeH = (int) Math.min(h, Math.round(targetH / scale));
        return new int[]{(w - takeW) / 2, (h - takeH) / 2, takeW, takeH};
    }
}
