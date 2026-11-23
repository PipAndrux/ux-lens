package dev.uxlens.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelPricingTest {

    @Test
    void pricesOpus55() {
        // 10k input * $4 + 2k output * $20 + 1k cache write * $5 + 50k cache read * $0.20, per million tokens
        assertThat(ModelPricing.costUsd("claude-opus-5-5", 10_000, 2_000, 1_000, 50_000))
                .isEqualByComparingTo(new BigDecimal("0.095"));
    }

    @Test
    void pricesAFallbackModelAtItsOwnRate() {
        assertThat(ModelPricing.costUsd("claude-opus-4-8", 1_000_000, 0, 0, 0))
                .isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void usesTheHighestKnownRateForAnUnknownModel() {
        assertThat(ModelPricing.costUsd("claude-something-new", 0, 1_000_000, 0, 0))
                .isEqualByComparingTo(new BigDecimal("25"));
    }
}
