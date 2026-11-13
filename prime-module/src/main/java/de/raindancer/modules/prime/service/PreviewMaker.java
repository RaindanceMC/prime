package de.raindancer.modules.prime.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A small JPEG of a map art picture for staff looking at it elsewhere (Discord). It has to fit into one request to
 * Hush with room to spare, so it shrinks and loses quality until it does.
 */
public final class PreviewMaker {

    public static final int LONG_SIDE = 384;
    private static final int SMALLEST_SIDE = 48;

    private PreviewMaker() {
    }

    /** @return the JPEG, or empty when not even a tiny one fits into {@code mostBytes} */
    public static byte[] jpeg(BufferedImage picture, int mostBytes) {
        double scale = Math.min(1.0, (double) LONG_SIDE / Math.max(picture.getWidth(), picture.getHeight()));
        float quality = 0.85f;
        while (true) {
            int w = Math.max(1, (int) Math.round(picture.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(picture.getHeight() * scale));
            byte[] out = encode(resize(picture, w, h), quality);
            if (out.length > 0 && out.length <= mostBytes) {
                return out;
            }
            if (quality > 0.55f) {
                quality -= 0.15f;
            } else if (Math.max(w, h) > SMALLEST_SIDE) {
                scale *= 0.75;
            } else {
                return new byte[0];
            }
        }
    }

    private static BufferedImage resize(BufferedImage picture, int w, int h) {
        // JPEG has no alpha: transparent parts go on the map's own background colour, a dark navy
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x0F1E3D));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(picture, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] encode(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException failed) {
            return new byte[0];
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
