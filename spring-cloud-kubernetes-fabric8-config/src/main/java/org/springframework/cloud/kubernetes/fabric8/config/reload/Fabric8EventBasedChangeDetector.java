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

package org.springframework.cloud.kubernetes.fabric8.config.reload;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Common functionality for Fabric8 event-based ConfigMap and Secret change detectors.
 *
 * @param <T> the Kubernetes resource type handled by the detector
 * @author wind57
 */
abstract class Fabric8EventBasedChangeDetector<T extends HasMetadata> extends ConfigurationChangeDetector {

	private final PropertySourceLocator propertySourceLocator;

	private final ConfigurableEnvironment environment;

	private final Class<? extends MapPropertySource> existingSourcesType;

	protected final KubernetesClient kubernetesClient;

	protected final List<SharedIndexInformer<T>> informers = new ArrayList<>();

	protected Fabric8EventBasedChangeDetector(AbstractEnvironment environment, KubernetesClient kubernetesClient,
			ConfigurationUpdateStrategy strategy, PropertySourceLocator propertySourceLocator,
			Class<? extends MapPropertySource> existingSourcesType) {
		super(strategy);
		this.environment = environment;
		this.kubernetesClient = kubernetesClient;
		this.propertySourceLocator = propertySourceLocator;
		this.existingSourcesType = existingSourcesType;
	}

	protected final void onEvent(T resource) {
		boolean reload = ConfigReloadUtil.reload(resource.getKind(), resource.toString(), propertySourceLocator,
				environment, existingSourcesType);
		if (reload) {
			reloadProperties();
		}
	}

	@PreDestroy
	protected void shutdown() {
		informers.forEach(SharedIndexInformer::close);
		// Ensure the kubernetes client is cleaned up from spare threads when shutting
		// down
		kubernetesClient.close();
	}

}
