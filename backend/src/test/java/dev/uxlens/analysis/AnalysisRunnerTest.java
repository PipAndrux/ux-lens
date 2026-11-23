package dev.uxlens.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.uxlens.TestcontainersConfiguration;
import dev.uxlens.ai.AnalysisProvider;
import dev.uxlens.ai.AnalysisProviderException;
import dev.uxlens.image.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnalysisRunnerTest {

    @Autowired AnalysisRunner runner;
    @Autowired AnalysisRepository analyses;
    @MockitoBean AnalysisProvider provider;

    @Test
    void storesTheProvidersMessageWhenItFails() {
        when(provider.analyze(any())).thenThrow(new AnalysisProviderException("The AI declined to analyze this image."));

        Analysis failed = runAndReload();

        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("The AI declined to analyze this image.");
    }

    @Test
    void hidesTheDetailsOfAnUnexpectedError() {
        when(provider.analyze(any())).thenThrow(new IllegalStateException("secret internal detail"));

        Analysis failed = runAndReload();

        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getErrorMessage()).doesNotContain("secret").contains("Something went wrong");
    }

    private Analysis runAndReload() {
        byte[] png = TestImages.png(64, 64);
        Analysis pending = analyses.save(new Analysis("a".repeat(64), "image/png", 64, 64, png));
        runner.run(pending.getId());
        return analyses.findById(pending.getId()).orElseThrow();
    }
}
