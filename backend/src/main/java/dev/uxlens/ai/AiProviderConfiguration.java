package dev.uxlens.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import dev.uxlens.usage.ApiUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link AnalysisProvider} from {@code uxlens.ai.provider}: {@code mock} (default, free) or {@code claude}.
 * Asking for Claude without an API key stops the app at startup instead of silently falling back, so it is always
 * clear whether real (paid) analyses are running. See docs/adr/0002-cost-free-by-default.md.
 */
@Configuration(proxyBeanMethods = false)
public class AiProviderConfiguration {

    static final String PROPERTY = "uxlens.ai.provider";

    @Bean
    @ConditionalOnProperty(name = PROPERTY, havingValue = MockAnalysisProvider.NAME, matchIfMissing = true)
    AnalysisProvider mockAnalysisProvider() {
        return new MockAnalysisProvider();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = PROPERTY, havingValue = ClaudeAnalysisProvider.NAME)
    static class Claude {

        @Bean(destroyMethod = "close")
        AnthropicClient anthropicClient(@Value("${uxlens.anthropic.api-key:}") String apiKey) {
            if (apiKey.isBlank()) {
                throw new IllegalStateException(
                        PROPERTY + "=claude needs the ANTHROPIC_API_KEY environment variable. "
                                + "Set it, or use " + PROPERTY + "=mock to run without the API.");
            }
            return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }

        @Bean
        AnalysisProvider claudeAnalysisProvider(AnthropicClient client,
                                                @Value("${uxlens.anthropic.model}") String model,
                                                ApiUsageRepository usage) {
            return new ClaudeAnalysisProvider(client, model, usage);
        }
    }
}
