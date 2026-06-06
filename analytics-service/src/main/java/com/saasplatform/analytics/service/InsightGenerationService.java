package com.saasplatform.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import com.saasplatform.context.RequestContextHolder;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightGenerationService {
    private final ChatClient chatClient;

    public record Query(String title, String metrics, String dateRange) {}

    public record InsightReport(String title, String summary, String recommendations) {}

    public InsightReport generateInsights(Query query) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        log.info("Generating insights for tenant: {}, query: {}", tenantId, query.title());

        BeanOutputConverter<InsightReport> converter = new BeanOutputConverter<>(InsightReport.class);

        String prompt = """
                Analyze the following SaaS analytics data and provide insights:

                Title: {0}
                Metrics: {1}
                Date Range: {2}

                Provide your analysis in the following format:
                {3}
                """;

        String response = chatClient.prompt()
                .user(user -> user.text(prompt,
                        query.title(),
                        query.metrics(),
                        query.dateRange(),
                        converter.getFormat()))
                .call()
                .content();

        return converter.convert(response);
    }

    public Flux<String> streamInsights(Query query) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        log.info("Streaming insights for tenant: {}, query: {}", tenantId, query.title());

        String prompt = """
                Analyze the following SaaS analytics data and provide real-time insights:

                Title: {0}
                Metrics: {1}
                Date Range: {2}

                Provide detailed analysis, breaking down the insights step by step.
                Focus on actionable recommendations for improving the business metrics.
                """;

        return chatClient.prompt()
                .user(user -> user.text(prompt,
                        query.title(),
                        query.metrics(),
                        query.dateRange()))
                .stream()
                .content();
    }
}
