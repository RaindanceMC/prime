package de.raindancer.modules.prime.service;

import de.raindancer.modules.prime.rules.MapArtRule;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;

/**
 * Fetches a player's image off the public internet and scales it to map size. Blocking: off the server's threads.
 *
 * <p>The host is resolved first and refused when any of its addresses is not public ({@link MapArtRule#isPublic}),
 * redirects are not followed, the body is cut off at the size limit, and the picture's dimensions are read before it
 * is decoded — a link is a stranger's input.
 */
public final class ImageFetcher {

    /** Larger than any sane picture, small enough that decoding it cannot eat the server's memory. */
    public static final int MOST_PIXELS_PER_SIDE = 8192;

    private final MapArtRule rule = new MapArtRule();
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build();

    public static final class Refused extends IOException {
        public Refused(String why) {
            super(why);
        }
    }

    public BufferedImage fetch(String link, long mostBytes) throws IOException, InterruptedException {
        java.util.Optional<String> wrong = rule.linkProblem(link);
        if (wrong.isPresent()) {
            throw new Refused(wrong.get());
        }
        URI uri = URI.create(link.strip());
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (!rule.isPublic(address)) {
                throw new Refused("that host is not on the public internet");
            }
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "RainsPrime-MapArt/1.0").header("Accept", "image/*").GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() / 100 != 2) {
                throw new Refused("the link answered " + response.statusCode()
                        + (response.statusCode() / 100 == 3 ? " (a redirect — use the image's own address)" : ""));
            }
            return decode(readAtMost(body, mostBytes));
        }
    }

    static byte[] readAtMost(InputStream in, long mostBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > mostBytes) {
                throw new Refused("the image is larger than " + mostBytes / (1024 * 1024) + " MB");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** PNG, JPEG, GIF (first frame) or BMP, checked for size before it is decoded. */
    public static BufferedImage decode(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? java.util.Collections.emptyIterator() : ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new Refused("that is not an image (PNG, JPEG, GIF or BMP)");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if (reader.getWidth(0) > MOST_PIXELS_PER_SIDE || reader.getHeight(0) > MOST_PIXELS_PER_SIDE) {
                    throw new Refused("the image is larger than " + MOST_PIXELS_PER_SIDE + " pixels on a side");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** The picture filling {@code width × height} maps, cut evenly where its shape differs, smoothly scaled. */
    public BufferedImage scale(BufferedImage source, int width, int height) {
        int targetW = width * MapArtRule.MAP_PIXELS;
        int targetH = height * MapArtRule.MAP_PIXELS;
        int[] take = rule.cover(source.getWidth(), source.getHeight(), targetW, targetH);
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, targetW, targetH, take[0], take[1], take[0] + take[2], take[1] + take[3], null);
        g.dispose();
        return out;
    }

    /** One map's part, row by row from the top left. */
    public BufferedImage tile(BufferedImage scaled, int index, int width) {
        int[] at = rule.tile(index, width);
        return scaled.getSubimage(at[0], at[1], at[2], at[3]);
    }
}
