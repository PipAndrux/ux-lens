package dev.uxlens.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "issue")
public class Issue {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueCategory category;

    /** Guideline the issue violates, e.g. "WCAG-1.4.3" or "NIELSEN-4". */
    @Column(name = "criterion_id")
    private String criterionId;

    @Column(nullable = false)
    private String suggestion;

    /** Null when the issue concerns the whole screen. */
    @Embedded
    private BoundingBox boundingBox;

    protected Issue() {
    }

    public Issue(String title, String description, Severity severity, IssueCategory category,
                 String criterionId, String suggestion, BoundingBox boundingBox) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.category = category;
        this.criterionId = criterionId;
        this.suggestion = suggestion;
        this.boundingBox = boundingBox;
    }

    void attachTo(Analysis analysis, int position) {
        this.analysis = analysis;
        this.position = position;
    }

    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Severity getSeverity() { return severity; }
    public IssueCategory getCategory() { return category; }
    public String getCriterionId() { return criterionId; }
    public String getSuggestion() { return suggestion; }
    public BoundingBox getBoundingBox() { return boundingBox; }
}
