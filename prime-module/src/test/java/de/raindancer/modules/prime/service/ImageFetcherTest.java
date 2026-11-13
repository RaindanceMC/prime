package de.raindancer.modules.prime.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageFetcherTest {

    private final ImageFetcher fetcher = new ImageFetcher();

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                image.setRGB(x, y, x < w / 2 ? Color.RED.getRGB() : Color.BLUE.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("an image is scaled to the maps and cut into 128-pixel tiles, left half on the left map")
    void scaleAndCut() throws Exception {
        BufferedImage scaled = fetcher.scale(ImageFetcher.decode(png(400, 200)), 2, 1);
        assertThat(scaled.getWidth()).isEqualTo(256);
        assertThat(scaled.getHeight()).isEqualTo(128);
        assertThat(new Color(fetcher.tile(scaled, 0, 2).getRGB(10, 64))).isEqualTo(Color.RED);
        assertThat(new Color(fetcher.tile(scaled, 1, 2).getRGB(118, 64))).isEqualTo(Color.BLUE);
    }

    @Test
    @DisplayName("what is not an image, too heavy or too large is refused before it is decoded")
    void refusals() throws Exception {
        assertThatThrownBy(() -> ImageFetcher.decode("<html>nope</html>".getBytes()))
                .isInstanceOf(ImageFetcher.Refused.class);
        assertThatThrownBy(() -> ImageFetcher.readAtMost(new ByteArrayInputStream(new byte[2048]), 1024))
                .isInstanceOf(ImageFetcher.Refused.class);
        assertThatThrownBy(() -> ImageFetcher.decode(png(ImageFetcher.MOST_PIXELS_PER_SIDE + 1, 1)))
                .isInstanceOf(ImageFetcher.Refused.class).hasMessageContaining("pixels");
    }

    @Test
    @DisplayName("a link to this machine or the network around it is refused without a request")
    void noWayIn() {
        assertThatThrownBy(() -> fetcher.fetch("https://127.0.0.1/a.png", 1_000_000))
                .isInstanceOf(ImageFetcher.Refused.class).hasMessageContaining("public");
        assertThatThrownBy(() -> fetcher.fetch("https://localhost/a.png", 1_000_000))
                .isInstanceOf(ImageFetcher.Refused.class);
        assertThatThrownBy(() -> fetcher.fetch("http://example.com/a.png", 1_000_000))
                .isInstanceOf(ImageFetcher.Refused.class).hasMessageContaining("https");
    }
}
