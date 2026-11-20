package dev.uxlens.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Validates an uploaded screenshot and normalizes it for analysis.
 *
 * <p>Order matters for safety: the format is checked from the magic bytes, then the dimensions are read from the
 * header <em>before</em> decoding, so a small file that claims to be a huge image (a "decompression bomb") is
 * rejected without allocating its pixels.
 */
@Component
public class ImageProcessor {

    /** Upload limit, also enforced by Spring's multipart settings. */
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    /** Longest side sent to the vision model. Larger images cost more tokens without better results. */
    public static final int MAX_LONG_SIDE = 1568;

    /** Refuse anything above this many pixels before decoding it. */
    static final long MAX_PIXELS = 40_000_000L;

    public ProcessedImage process(byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidImageException("The file is empty.");
        }
        if (data.length > MAX_FILE_BYTES) {
            throw new ImageTooLargeException("The file is larger than 5 MB.");
        }
        ImageFormat format = ImageFormat.detect(data)
                .orElseThrow(() -> new InvalidImageException("Unsupported file type. Upload a PNG, JPEG or WebP image."));

        BufferedImage image = decode(data);
        BufferedImage scaled = scaleDown(image);
        return new ProcessedImage(encodePng(scaled), scaled.getWidth(), scaled.getHeight(), sha256(data), format);
    }

    private static BufferedImage decode(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new InvalidImageException("The image could not be read.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new InvalidImageException("The image has no size.");
                }
                if (width * height > MAX_PIXELS) {
                    throw new InvalidImageException("The image is too large: " + width + "×" + height + " pixels.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException invalid) {
                throw invalid;
            }
            throw new InvalidImageException("The image could not be read. The file may be damaged.", e);
        }
    }

    /** Halves the image until it is close to the target, then does one final high-quality step. */
    static BufferedImage scaleDown(BufferedImage source) {
        int longSide = Math.max(source.getWidth(), source.getHeight());
        boolean hasAlpha = source.getColorModel().hasAlpha();
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (longSide <= MAX_LONG_SIDE) {
            return convert(source, type);
        }
        double ratio = (double) MAX_LONG_SIDE / longSide;
        int targetW = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int targetH = Math.max(1, (int) Math.round(source.getHeight() * ratio));

        BufferedImage current = source;
        int w = source.getWidth();
        int h = source.getHeight();
        do {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            current = draw(current, w, h, type);
        } while (w != targetW || h != targetH);
        return current;
    }

    private static BufferedImage convert(BufferedImage source, int type) {
        return source.getType() == type ? source : draw(source, source.getWidth(), source.getHeight(), type);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, int type) {
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("No PNG writer available");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
