package dev.uxlens.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageFormatTest {

    @Test
    void detectsFormatsFromMagicBytes() {
        assertThat(ImageFormat.detect(TestImages.png(10, 10))).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(TestImages.jpeg(10, 10))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(TestImages.webp())).contains(ImageFormat.WEBP);
    }

    @Test
    void rejectsOtherContentWhateverItIsCalled() {
        assertThat(ImageFormat.detect("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(ImageFormat.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect(new byte[] {(byte) 0x89, 'P'})).isEmpty(); // truncated header
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
    }
}
