package com.lamelo.agent.ai.chatagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamelo.agent.ai.chatagent.config.ChatAgentProperties;
import com.lamelo.agent.ai.prompt.PromptTemplateService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void generatesRecommendationsFromStreamingModelResponse() {
        ChatAgentProperties properties = new ChatAgentProperties();
        properties.setRecommendationTimeoutMs(15000L);
        ObservedChatModelService chatModel = mock(ObservedChatModelService.class);
        PromptTemplateService templates = mock(PromptTemplateService.class);
        when(templates.render(any(), any())).thenReturn("recommend three questions");
        when(chatModel.streamText(eq("recommendation"), isNull(), any(), isNull()))
            .thenReturn(Flux.just("[\"问题一\",", "\"问题二\",\"问题三\"]"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            RecommendationService service = new RecommendationService(properties, new ObjectMapper(), executor,
                chatModel, templates);

            assertThat(service.generateRecommendations("原问题", "回答", List.of(), null))
                .containsExactly("问题一", "问题二", "问题三");
            verify(chatModel).streamText(eq("recommendation"), isNull(), any(), isNull());
        } finally {
            executor.shutdownNow();
        }
    }
}
