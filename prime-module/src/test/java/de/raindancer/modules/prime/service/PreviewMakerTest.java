package de.raindancer.modules.prime.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewMakerTest {

    private static BufferedImage noise(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                image.setRGB(x, y, 0xFF000000 | random.nextInt(0xFFFFFF));
            }
        }
        return image;
    }

    @Test
    @DisplayName("a preview is a JPEG no longer than the budget, at most 384 pixels on its long side, same shape")
    void fitsTheBudget() throws Exception {
        byte[] jpeg = PreviewMaker.jpeg(noise(512, 256), 36 * 1024);

        assertThat(jpeg.length).isLessThanOrEqualTo(36 * 1024).isGreaterThan(0);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(back.getWidth()).isLessThanOrEqualTo(PreviewMaker.LONG_SIDE);
        assertThat(back.getWidth()).isEqualTo(back.getHeight() * 2);
    }

    @Test
    @DisplayName("a small picture is not blown up")
    void small() throws Exception {
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(PreviewMaker.jpeg(noise(128, 128), 36 * 1024)));
        assertThat(back.getWidth()).isEqualTo(128);
    }

    @Test
    @DisplayName("a budget too small for any picture gives nothing rather than a broken one")
    void hopeless() {
        assertThat(PreviewMaker.jpeg(noise(256, 256), 10)).isEmpty();
    }
}
