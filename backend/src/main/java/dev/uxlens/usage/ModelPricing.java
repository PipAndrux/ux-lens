package dev.uxlens.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * List prices of the Claude models this app may be billed for, in USD per million tokens. The refusal fallback can
 * answer with a different model than the one requested, so the cost is computed from the model that actually
 * served the call. Prices change: check them against https://claude.com/pricing when updating models.
 */
public final class ModelPricing {

    private static final Logger log = LoggerFactory.getLogger(ModelPricing.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /** Per-million-token rates; cache writes are the 5-minute TTL price. */
    record Rates(BigDecimal input, BigDecimal output, BigDecimal cacheWrite, BigDecimal cacheRead) {
        static Rates of(String input, String output, String cacheWrite, String cacheRead) {
            return new Rates(new BigDecimal(input), new BigDecimal(output), new BigDecimal(cacheWrite),
                    new BigDecimal(cacheRead));
        }
    }

    private static final Rates OPUS_5 = Rates.of("5", "25", "6.25", "0.50");

    private static final Map<String, Rates> RATES = Map.of(
            "claude-opus-5-5", Rates.of("4", "20", "5", "0.20"),
            "claude-opus-5", OPUS_5,
            "claude-opus-4-8", OPUS_5,
            "claude-sonnet-5-5", Rates.of("2", "10", "2.50", "0.10"));

    /** Used for unknown models: the highest rates above, so the daily budget guard errs on the safe side. */
    private static final Rates UNKNOWN = OPUS_5;

    private ModelPricing() {
    }

    /**
     * Cost of one call. {@code inputTokens} excludes cached tokens, as in the API's usage block.
     */
    public static BigDecimal costUsd(String model, long inputTokens, long outputTokens, long cacheWriteTokens,
                                     long cacheReadTokens) {
        Rates rates = RATES.get(model);
        if (rates == null) {
            log.warn("No price known for model {}; using the highest known rates", model);
            rates = UNKNOWN;
        }
        BigDecimal total = rates.input().multiply(BigDecimal.valueOf(inputTokens))
                .add(rates.output().multiply(BigDecimal.valueOf(outputTokens)))
                .add(rates.cacheWrite().multiply(BigDecimal.valueOf(cacheWriteTokens)))
                .add(rates.cacheRead().multiply(BigDecimal.valueOf(cacheReadTokens)));
        return total.divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
