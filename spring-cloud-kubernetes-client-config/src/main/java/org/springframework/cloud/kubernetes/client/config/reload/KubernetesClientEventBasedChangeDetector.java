/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
abstract class KubernetesClientEventBasedChangeDetector extends ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientEventBasedChangeDetector.class);

	private final PropertySourceLocator propertySourceLocator;

	private final ConfigurableEnvironment environment;

	private final Class<? extends MapPropertySource> existingSourcesType;

	protected final List<SharedIndexInformer<?>> informers = new ArrayList<>();

	protected final List<SharedInformerFactory> factories = new ArrayList<>();

	protected KubernetesClientEventBasedChangeDetector(ConfigurationUpdateStrategy strategy,
			PropertySourceLocator propertySourceLocator, ConfigurableEnvironment environment,
			Class<? extends MapPropertySource> existingSourcesType) {
		super(strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.environment = environment;
		this.existingSourcesType = existingSourcesType;
	}

	protected final Map<String, String> resolveLabelSelector(boolean enableReloadFiltering,
			Map<String, String> configuredLabels, String replacementProperty) {

		if (!enableReloadFiltering) {
			return configuredLabels;
		}

		LOG.warn(() -> "enable reload filtering is deprecated and will be removed in the next major release");
		LOG.warn(() -> "use " + replacementProperty + " instead");

		if (!configuredLabels.isEmpty()) {
			LOG.warn(() -> replacementProperty + " is not empty, but "
					+ "spring.cloud.kubernetes.reload.enable-reload-filtering is enabled and will override it");
		}

		return Map.of(ConfigReloadProperties.RELOAD_LABEL_FILTER, "true");
	}

	protected void onEvent(KubernetesObject resource) {
		boolean reload = ConfigReloadUtil.reload(resource.getKind(), resource.toString(), propertySourceLocator,
				environment, existingSourcesType);
		if (reload) {
			reloadProperties();
		}
	}

	@PreDestroy
	protected void shutdown() {
		informers.forEach(SharedInformer::stop);
		factories.forEach(SharedInformerFactory::stopAllRegisteredInformers);
	}

}
