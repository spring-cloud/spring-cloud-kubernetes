/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.registry.KubernetesRegistration;
import org.springframework.cloud.kubernetes.registry.KubernetesServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name="spring.cloud.kubernetes.enabled", matchIfMissing = true)
public class KubernetesDiscoveryClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.cloud.kubernetes.discovery.enabled",matchIfMissing = true)
	public DiscoveryClient discoveryClient(KubernetesClient client,
										   KubernetesDiscoveryProperties properties) {
		return new KubernetesDiscoveryClient(client, properties);
	}

	@Bean
	public KubernetesServiceRegistry getServiceRegistry() {
		return new KubernetesServiceRegistry();
	}

	@Bean
	public KubernetesRegistration getRegistration(KubernetesClient client,
												  KubernetesDiscoveryProperties properties) {
		return new KubernetesRegistration(client, properties);
	}

	@Bean
	@Primary
	public KubernetesDiscoveryProperties getKubernetesDiscoveryProperties() {
		return new KubernetesDiscoveryProperties();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.cloud.kubernetes.discovery.catalog-services-watch.enabled", matchIfMissing = true)
	public KubernetesCatalogWatch kubernetesCatalogWatch(KubernetesClient client) {
		return new KubernetesCatalogWatch(client);
	}
}
