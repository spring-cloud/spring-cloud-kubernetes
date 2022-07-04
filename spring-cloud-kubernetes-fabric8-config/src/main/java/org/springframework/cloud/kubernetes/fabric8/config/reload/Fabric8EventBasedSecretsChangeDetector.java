/*
 * Copyright 2013-2022 the original author or authors.
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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;

/**
 * An event based change detector that subscribes to changes in secrets and fire a reload
 * when something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 * @author Kris Iyer
 */
public class Fabric8EventBasedSecretsChangeDetector extends ConfigurationChangeDetector {

	private final Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator;

	private final KubernetesClient kubernetesClient;

	private final boolean monitorSecrets;

	private SharedIndexInformer<Secret> informer;

	public Fabric8EventBasedSecretsChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator) {
		super(environment, properties, strategy);
		this.kubernetesClient = kubernetesClient;
		this.fabric8SecretsPropertySourceLocator = fabric8SecretsPropertySourceLocator;
		this.monitorSecrets = properties.isMonitoringSecrets();
	}

	@PreDestroy
	private void shutdown() {
		if (informer != null) {
			log.debug("closing secrets informer");
			informer.close();
		}
		// Ensure the kubernetes client is cleaned up from spare threads when shutting
		// down
		kubernetesClient.close();
	}

	@PostConstruct
	private void inform() {
		if (monitorSecrets) {
			log.info("Kubernetes event-based secrets change detector activated");
			informer = kubernetesClient.secrets().inform();
			informer.addEventHandler(new ResourceEventHandler<>() {
				@Override
				public void onAdd(Secret secret) {
					onEvent(secret);
				}

				@Override
				public void onUpdate(Secret oldSecret, Secret newSecret) {
					onEvent(newSecret);
				}

				@Override
				public void onDelete(Secret secret, boolean deletedFinalStateUnknown) {
					onEvent(secret);
				}

// leave as comment on purpose, may be this will be useful in the future
//				@Override
//				public void onNothing() {
//					boolean isStoreEmpty = informer.getStore().list().isEmpty();
//					if(!isStoreEmpty) {
//						// HTTP_GONE, thus re-inform
//						inform();
//					}
//				}
			});
		}
	}

	protected void onEvent(Secret secret) {
		log.debug("onEvent secrets: " + secret.toString());
		boolean changed = changed(locateMapPropertySources(fabric8SecretsPropertySourceLocator, environment),
			findPropertySources(Fabric8SecretsPropertySource.class));
		if (changed) {
			log.info("Detected change in secrets");
			reloadProperties();
		}
	}

}
