package com.saasplatform.analytics.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are an analytics expert for SaaS platforms. Provide insightful analysis based on the data provided.
                        Always focus on actionable insights and key metrics.
                        Format your responses clearly with sections and bullet points.""")
                .build();
    }

    @Bean
    public SimpleVectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        return new SimpleVectorStore(embeddingModel);
    }
}
