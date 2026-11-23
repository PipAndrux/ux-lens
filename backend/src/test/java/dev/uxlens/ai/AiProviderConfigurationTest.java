package dev.uxlens.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.uxlens.usage.ApiUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiProviderConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiProviderConfiguration.class)
            .withBean(ApiUsageRepository.class, () -> mock(ApiUsageRepository.class))
            .withPropertyValues("uxlens.anthropic.model=claude-opus-5-5");

    @Test
    void usesTheFreeMockByDefault() {
        runner.run(context -> assertThat(context).getBean(AnalysisProvider.class)
                .isInstanceOf(MockAnalysisProvider.class));
    }

    @Test
    void usesClaudeWhenSelectedAndAKeyIsSet() {
        runner.withPropertyValues("uxlens.ai.provider=claude", "uxlens.anthropic.api-key=test-key-not-real")
                .run(context -> assertThat(context).getBean(AnalysisProvider.class)
                        .isInstanceOf(ClaudeAnalysisProvider.class));
    }

    @Test
    void refusesToStartWithClaudeSelectedButNoKey() {
        runner.withPropertyValues("uxlens.ai.provider=claude", "uxlens.anthropic.api-key=")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("needs the ANTHROPIC_API_KEY"));
    }
}
