package dev.uxlens.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Token usage and cost of one model call. */
@Entity
@Table(name = "api_usage")
public class ApiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plain id rather than a relation: usage rows outlive the analysis they belonged to. */
    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private int cacheReadTokens;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd;

    protected ApiUsage() {
    }

    public ApiUsage(UUID analysisId, String provider, String model, int inputTokens, int outputTokens,
                    int cacheReadTokens, BigDecimal costUsd) {
        this.analysisId = analysisId;
        this.createdAt = Instant.now();
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.costUsd = costUsd;
    }

    public Long getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public int getCacheReadTokens() { return cacheReadTokens; }
    public BigDecimal getCostUsd() { return costUsd; }
}
