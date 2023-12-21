/*
 * Copyright 2013-2023 the original author or authors.
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

import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnHttpDiscoveryCatalogWatcherEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesCatalogWatcherEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.CATALOG_WATCHER_DEFAULT_DELAY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.CATALOG_WATCH_PROPERTY_NAME;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesCatalogWatcherEnabled
@ConditionalOnHttpDiscoveryCatalogWatcherEnabled
@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
class KubernetesCatalogWatchAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesCatalogWatchAutoConfiguration.class));

	// this has to be a RestTemplateBuilder and not a WebClientBuilder, otherwise
	// we need the webflux dependency, and it might leak into client's dependencies
	// which is not always desirable.
	@Bean
	@ConditionalOnMissingBean
	RestTemplateBuilder restTemplateBuilder() {
		return new RestTemplateBuilder();
	}

	@Bean
	@ConditionalOnMissingBean
	KubernetesCatalogWatch kubernetesCatalogWatch(RestTemplateBuilder builder, KubernetesDiscoveryProperties properties,
			Environment environment) {

		String watchDelay = environment.getProperty(CATALOG_WATCH_PROPERTY_NAME);
		if (watchDelay != null) {
			LOG.debug("using delay : " + watchDelay);
		}
		else {
			LOG.debug("using default watch delay : " + CATALOG_WATCHER_DEFAULT_DELAY);
		}

		return new KubernetesCatalogWatch(builder, properties);
	}

}
