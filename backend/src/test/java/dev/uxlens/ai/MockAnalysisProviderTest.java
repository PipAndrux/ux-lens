package dev.uxlens.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockAnalysisProviderTest {

    @Test
    void returnsASampleAuditThatSaysItIsNotReal() {
        AnalysisResult result = new MockAnalysisProvider()
                .analyze(new AnalysisRequest(UUID.randomUUID(), new byte[0], 800, 600));

        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.summary()).contains("no AI was used");
        assertThat(result.findings()).hasSize(5);
        assertThat(result.findings()).extracting(AnalysisResult.Finding::criterionId)
                .allMatch(id -> ClaudeAnalysisProvider.criterionId(id) != null);
    }
}
