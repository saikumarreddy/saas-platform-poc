package com.saasplatform.monitoring;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.datadog.DatadogMeterRegistry;
import io.micrometer.datadog.DatadogConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Slf4j
@Configuration
@EnableAspectJAutoProxy
public class DatadogConfig {

    @Bean
    public DatadogConfig datadogConfig() {
        return new DatadogConfig() {
            @Override
            public String apiKey() {
                return System.getenv("DD_API_KEY");
            }

            @Override
            public String applicationName() {
                return System.getenv("DD_SERVICE_NAME");
            }

            @Override
            public String environment() {
                return System.getenv("DD_ENV");
            }

            @Override
            public boolean enabled() {
                return System.getenv("DD_API_KEY") != null;
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            String serviceName = System.getenv("DD_SERVICE_NAME");
            String environment = System.getenv("DD_ENV");

            registry.config().commonTags(
                    Tags.of(
                            "service", serviceName != null ? serviceName : "unknown",
                            "env", environment != null ? environment : "local",
                            "version", "1.0.0"
                    )
            );

            log.info("Datadog metrics configured for service: {}, env: {}", serviceName, environment);
        };
    }
}
