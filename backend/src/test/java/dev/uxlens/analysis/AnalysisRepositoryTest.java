package dev.uxlens.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.uxlens.TestcontainersConfiguration;
import dev.uxlens.usage.ApiUsage;
import dev.uxlens.usage.ApiUsageRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the real Flyway migrations against Postgres and checks the entities against them. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AnalysisRepositoryTest {

    @Autowired AnalysisRepository analyses;
    @Autowired ApiUsageRepository usage;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void savesAndReloadsACompletedAnalysisWithOrderedIssues() {
        Analysis analysis = newAnalysis("a".repeat(64));
        analysis.markRunning();
        analysis.complete("mock", "mock-1", "Two issues found.", (short) 62, (short) 80, (short) 90, List.of(
                new Issue("Low contrast on the primary button", "White text on light blue.", Severity.MAJOR,
                        IssueCategory.ACCESSIBILITY, "WCAG-1.4.3", "Darken the button background.",
                        new BoundingBox(0.4f, 0.7f, 0.2f, 0.08f)),
                new Issue("No visible page title", "The screen has no heading.", Severity.MINOR,
                        IssueCategory.VISUAL_HIERARCHY, "NIELSEN-6", "Add an H1 at the top.", null)));
        analyses.saveAndFlush(analysis);
        em.clear();

        Analysis reloaded = analyses.findById(analysis.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(reloaded.getScoreAccessibility()).isEqualTo((short) 62);
        assertThat(reloaded.getIssues()).extracting(Issue::getTitle)
                .containsExactly("Low contrast on the primary button", "No visible page title");
        assertThat(reloaded.getIssues().get(0).getBoundingBox()).isEqualTo(new BoundingBox(0.4f, 0.7f, 0.2f, 0.08f));
        assertThat(reloaded.getIssues().get(1).getBoundingBox()).isNull();
    }

    @Test
    void findsThePreviousResultForTheSameImage() {
        String hash = "b".repeat(64);
        Analysis done = newAnalysis(hash);
        done.markRunning();
        done.complete("mock", "mock-1", "ok", (short) 90, (short) 90, (short) 90, List.of());
        analyses.save(done);
        analyses.save(newAnalysis(hash)); // still pending, must be ignored

        assertThat(analyses.findFirstByImageSha256AndStatusOrderByCreatedAtDesc(hash, AnalysisStatus.DONE))
                .get().extracting(Analysis::getId).isEqualTo(done.getId());
        assertThat(analyses.findFirstByImageSha256AndStatusOrderByCreatedAtDesc("c".repeat(64), AnalysisStatus.DONE))
                .isEmpty();
    }

    @Test
    void databaseRejectsABoxOutsideTheImage() {
        Analysis analysis = newAnalysis("d".repeat(64));
        analyses.saveAndFlush(analysis);

        // Bypass the Java check to prove the database constraint holds on its own.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO issue (id, analysis_id, position, title, description, severity, category,
                                   suggestion, bbox_x, bbox_y, bbox_w, bbox_h)
                VALUES (gen_random_uuid(), ?, 0, 't', 'd', 'MINOR', 'USABILITY', 's', 0.9, 0.1, 0.5, 0.1)
                """, analysis.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sumsTheCostOfRecentCalls() {
        usage.save(new ApiUsage(null, "claude", "claude-opus-5-5", 6000, 3000, 0, new BigDecimal("0.084000")));
        usage.save(new ApiUsage(null, "claude", "claude-opus-5-5", 6000, 2000, 4000, new BigDecimal("0.050000")));

        assertThat(usage.totalCostSince(Instant.now().minus(1, ChronoUnit.DAYS)))
                .isEqualByComparingTo("0.134");
        assertThat(usage.totalCostSince(Instant.now().plus(1, ChronoUnit.DAYS)))
                .isEqualByComparingTo("0");
    }

    @Test
    void stateMachineRejectsCompletingAPendingAnalysis() {
        Analysis analysis = newAnalysis("e".repeat(64));
        assertThatThrownBy(() -> analysis.complete("mock", "m", "s", (short) 1, (short) 1, (short) 1, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Analysis newAnalysis(String hash) {
        return new Analysis(hash, "image/png", 1280, 800, new byte[] {1, 2, 3});
    }
}
