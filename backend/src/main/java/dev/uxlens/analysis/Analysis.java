package dev.uxlens.analysis;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analysis")
public class Analysis {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(name = "image_sha256", nullable = false, length = 64)
    private String imageSha256;

    @Column(name = "image_media_type", nullable = false)
    private String imageMediaType;

    @Column(name = "image_width", nullable = false)
    private int imageWidth;

    @Column(name = "image_height", nullable = false)
    private int imageHeight;

    /**
     * The normalized PNG. LAZY is only a hint here (true lazy loading of a column needs Hibernate bytecode
     * enhancement), so list views such as the history page must use a projection that leaves this column out.
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    private String provider;

    private String model;

    private String summary;

    @Column(name = "score_accessibility")
    private Short scoreAccessibility;

    @Column(name = "score_hierarchy")
    private Short scoreHierarchy;

    @Column(name = "score_consistency")
    private Short scoreConsistency;

    @Column(name = "error_message")
    private String errorMessage;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Issue> issues = new ArrayList<>();

    protected Analysis() {
    }

    public Analysis(String imageSha256, String imageMediaType, int imageWidth, int imageHeight, byte[] imageData) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = AnalysisStatus.PENDING;
        this.imageSha256 = imageSha256;
        this.imageMediaType = imageMediaType;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.imageData = imageData;
    }

    public void markRunning() {
        requireStatus(AnalysisStatus.PENDING);
        this.status = AnalysisStatus.RUNNING;
    }

    /** Stores the result of a finished analysis. Issues keep the order the provider returned them in. */
    public void complete(String provider, String model, String summary, short accessibility, short hierarchy,
                         short consistency, List<Issue> newIssues) {
        requireStatus(AnalysisStatus.RUNNING);
        this.provider = provider;
        this.model = model;
        this.summary = summary;
        this.scoreAccessibility = checkScore(accessibility);
        this.scoreHierarchy = checkScore(hierarchy);
        this.scoreConsistency = checkScore(consistency);
        this.issues.clear();
        for (int i = 0; i < newIssues.size(); i++) {
            Issue issue = newIssues.get(i);
            issue.attachTo(this, i);
            this.issues.add(issue);
        }
        this.status = AnalysisStatus.DONE;
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = AnalysisStatus.FAILED;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    private void requireStatus(AnalysisStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Analysis " + id + " is " + status + ", expected " + expected);
        }
    }

    private static short checkScore(short score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score must be between 0 and 100: " + score);
        }
        return score;
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public AnalysisStatus getStatus() { return status; }
    public String getImageSha256() { return imageSha256; }
    public String getImageMediaType() { return imageMediaType; }
    public int getImageWidth() { return imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public byte[] getImageData() { return imageData; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getSummary() { return summary; }
    public Short getScoreAccessibility() { return scoreAccessibility; }
    public Short getScoreHierarchy() { return scoreHierarchy; }
    public Short getScoreConsistency() { return scoreConsistency; }
    public String getErrorMessage() { return errorMessage; }
    public List<Issue> getIssues() { return Collections.unmodifiableList(issues); }
}
