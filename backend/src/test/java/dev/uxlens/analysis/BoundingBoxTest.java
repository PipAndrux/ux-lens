package dev.uxlens.analysis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BoundingBoxTest {

    @Test
    void acceptsABoxInsideTheImage() {
        assertThatCode(() -> new BoundingBox(0.1f, 0.2f, 0.5f, 0.3f)).doesNotThrowAnyException();
        assertThatCode(() -> new BoundingBox(0f, 0f, 1f, 1f)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "-0.1, 0, 0.5, 0.5",  // starts left of the image
            "0, -0.1, 0.5, 0.5",  // starts above the image
            "0.1, 0.1, 0, 0.5",   // zero width
            "0.1, 0.1, 0.5, 0",   // zero height
            "0.6, 0.1, 0.5, 0.5", // overflows to the right
            "0.1, 0.6, 0.5, 0.5", // overflows at the bottom
    })
    void rejectsABoxOutsideTheImage(float x, float y, float w, float h) {
        assertThatThrownBy(() -> new BoundingBox(x, y, w, h)).isInstanceOf(IllegalArgumentException.class);
    }
}
