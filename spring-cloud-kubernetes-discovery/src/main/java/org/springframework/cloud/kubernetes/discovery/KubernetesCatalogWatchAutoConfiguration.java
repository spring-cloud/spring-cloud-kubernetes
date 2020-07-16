/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.kubernetes.KubernetesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration for catalog watcher.
 *
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(name = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@AutoConfigureAfter({ KubernetesAutoConfiguration.class })
public class KubernetesCatalogWatchAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(
			name = "spring.cloud.kubernetes.discovery.catalog-services-watch.enabled",
			matchIfMissing = true)
	public KubernetesCatalogWatch kubernetesCatalogWatch(KubernetesClient client,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesCatalogWatch(client, properties);
	}

}
