/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.fabric8.spring.cloud.kubernetes.zipkin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.cloud.sleuth.zipkin.HttpZipkinSpanReporter;
import org.springframework.cloud.sleuth.zipkin.ZipkinAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.Span;

import java.util.List;

@Configuration
@EnableConfigurationProperties(KubernetesZipkinDiscoveryProperties.class)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.zipkin.discovery.enabled", matchIfMissing = true)
@AutoConfigureBefore(ZipkinAutoConfiguration.class)
public class ZipkinKubernetesAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinKubernetesAutoConfiguration.class);

    @Bean
    public ZipkinSpanReporter reporter(DiscoveryClient discoveryClient, KubernetesZipkinDiscoveryProperties discoveryProperties, SpanMetricReporter spanMetricReporter, ZipkinProperties zipkin) {
        String serviceName = discoveryProperties.getServiceName();
        List<ServiceInstance> services = discoveryClient.getInstances(serviceName);
        String serviceUrl = services.stream()
                .findFirst()
                .map(s -> s.getUri().toString())
                .orElse(null);

        LOGGER.warn("No service with name: ["+serviceName+"] found. Falling back to NullZipkinSpanReporter.");

        return serviceUrl == null || serviceUrl.isEmpty()
                ? new NullZipkinSpanReporter()
                : new HttpZipkinSpanReporter(serviceUrl, zipkin.getFlushInterval(), zipkin.getCompression().isEnabled(), spanMetricReporter);
    }

    static final class NullZipkinSpanReporter implements ZipkinSpanReporter {

        @Override
        public void report(Span span) {

        }
    }
}
