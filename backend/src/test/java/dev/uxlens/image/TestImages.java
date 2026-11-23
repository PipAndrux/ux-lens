package dev.uxlens.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Builds real image files in memory for tests. */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(screen(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }

    public static byte[] transparentPng(int width, int height) {
        return encode(screen(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(screen(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    /** A 320×200 WebP made with Pillow; the JDK can't write WebP. */
    public static byte[] webp() {
        try (InputStream in = TestImages.class.getResourceAsStream("/images/sample.webp")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage screen(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(30, 90, 200));
        g.fillRect(width / 10, height / 10, width / 2, height / 8);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
