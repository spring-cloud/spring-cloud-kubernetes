/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.fabric8.spring.cloud.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.discovery.enabled", matchIfMissing = true)
public class KubernetesDiscoveryClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KubernetesDiscoveryClient kubernetesDiscoveryClient(KubernetesClient client, KubernetesDiscoveryProperties properties) {
        return new KubernetesDiscoveryClient(client, properties.getServiceName());
    }

    @Bean
    public KubernetesDiscoveryLifecycle kubernetesDiscoveryLifecycle(KubernetesClient client, KubernetesDiscoveryProperties properties) {
        return new KubernetesDiscoveryLifecycle(client, properties);
    }
}
