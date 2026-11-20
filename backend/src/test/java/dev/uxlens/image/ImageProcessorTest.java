package dev.uxlens.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void keepsASmallImageAtItsSizeAndReencodesAsPng() throws IOException {
        ProcessedImage result = processor.process(TestImages.jpeg(800, 600));

        assertThat(result.width()).isEqualTo(800);
        assertThat(result.height()).isEqualTo(600);
        assertThat(result.originalFormat()).isEqualTo(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(result.png())).contains(ImageFormat.PNG);
        assertThat(ImageIO.read(new ByteArrayInputStream(result.png())).getWidth()).isEqualTo(800);
    }

    @Test
    void scalesALargeScreenshotDownKeepingTheAspectRatio() {
        ProcessedImage result = processor.process(TestImages.png(3200, 2000));

        assertThat(result.width()).isEqualTo(ImageProcessor.MAX_LONG_SIDE);
        assertThat(result.height()).isEqualTo(980); // 2000 × 1568 / 3200
    }

    @Test
    void scalesATallMobileScreenshotByItsHeight() {
        ProcessedImage result = processor.process(TestImages.png(1170, 2532));

        assertThat(result.height()).isEqualTo(ImageProcessor.MAX_LONG_SIDE);
        assertThat(result.width()).isEqualTo(725); // 1170 × 1568 / 2532
    }

    @Test
    void readsWebp() {
        ProcessedImage result = processor.process(TestImages.webp());

        assertThat(result.originalFormat()).isEqualTo(ImageFormat.WEBP);
        assertThat(result.width()).isEqualTo(320);
        assertThat(result.height()).isEqualTo(200);
    }

    @Test
    void keepsTransparency() throws IOException {
        ProcessedImage result = processor.process(TestImages.transparentPng(400, 300));

        assertThat(ImageIO.read(new ByteArrayInputStream(result.png())).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void hashesTheOriginalFileSoTheSameUploadGetsTheSameHash() {
        byte[] file = TestImages.png(100, 100);

        String hash = processor.process(file).sha256();

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(processor.process(file.clone()).sha256()).isEqualTo(hash);
        assertThat(processor.process(TestImages.png(101, 100)).sha256()).isNotEqualTo(hash);
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> processor.process(new byte[0]))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("empty");
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        byte[] big = Arrays.copyOf(TestImages.png(10, 10), ImageProcessor.MAX_FILE_BYTES + 1);

        assertThatThrownBy(() -> processor.process(big)).isInstanceOf(ImageTooLargeException.class);
    }

    @Test
    void rejectsAFileThatIsNotAnImageEvenIfNamedLikeOne() {
        assertThatThrownBy(() -> processor.process("GIF89a not supported".getBytes()))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("PNG, JPEG or WebP");
    }

    @Test
    void rejectsADamagedImage() {
        byte[] truncated = Arrays.copyOf(TestImages.png(200, 200), 40);

        assertThatThrownBy(() -> processor.process(truncated)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void rejectsADecompressionBombBeforeDecodingIt() throws IOException {
        // A tiny file whose header claims 50,000 × 50,000 pixels (2.5 billion).
        byte[] bomb = pngHeaderClaiming(50_000, 50_000);

        assertThat(bomb.length).isLessThan(1000);
        assertThatThrownBy(() -> processor.process(bomb))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("too large");
    }

    /** Writes a 1×1 PNG and rewrites the width and height in its IHDR chunk (CRC left stale: header is read first). */
    private static byte[] pngHeaderClaiming(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] png = out.toByteArray();
        writeInt(png, 16, width);  // IHDR data starts at byte 16: width, then height
        writeInt(png, 20, height);
        return png;
    }

    private static void writeInt(byte[] b, int offset, int value) {
        b[offset] = (byte) (value >>> 24);
        b[offset + 1] = (byte) (value >>> 16);
        b[offset + 2] = (byte) (value >>> 8);
        b[offset + 3] = (byte) value;
    }
}
