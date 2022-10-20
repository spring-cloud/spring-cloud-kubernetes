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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.namespaces;

/**
 * An event based change detector that subscribes to changes in secrets and fire a reload
 * when something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 * @author Kris Iyer
 */
public class Fabric8EventBasedSecretsChangeDetector extends ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(Fabric8EventBasedSecretsChangeDetector.class));

	private final Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator;

	private final KubernetesClient kubernetesClient;

	private final boolean monitorSecrets;

	private final List<SharedIndexInformer<Secret>> informers = new ArrayList<>();

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	public Fabric8EventBasedSecretsChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		super(environment, properties, strategy);
		this.kubernetesClient = kubernetesClient;
		this.fabric8SecretsPropertySourceLocator = fabric8SecretsPropertySourceLocator;
		this.enableReloadFiltering = properties.enableReloadFiltering();
		monitorSecrets = properties.monitoringSecrets();
		namespaces = namespaces(kubernetesClient, namespaceProvider, properties, "secrets");
	}

	@PreDestroy
	private void shutdown() {
		informers.forEach(SharedInformer::close);
		// Ensure the kubernetes client is cleaned up from spare threads when shutting
		// down
		kubernetesClient.close();
	}

	@PostConstruct
	private void inform() {
		if (monitorSecrets) {
			LOG.info("Kubernetes event-based secrets change detector activated");

			namespaces.forEach(namespace -> {
				SharedIndexInformer<Secret> informer;
				if (enableReloadFiltering) {
					informer = kubernetesClient.secrets().inNamespace(namespace)
							.withLabels(Map.of(ConfigReloadProperties.RELOAD_LABEL_FILTER, "true")).inform();
					LOG.debug("added secret informer for namespace : " + namespace + " with enabled filter");
				}
				else {
					informer = kubernetesClient.secrets().inNamespace(namespace).inform();
					LOG.debug("added secret informer for namespace : " + namespace);
				}

				informer.addEventHandler(new SecretInformerAwareEventHandler(informer));
				informers.add(informer);
			});
		}
	}

	protected void onEvent(Secret secret) {

		boolean reload = ConfigReloadUtil.reload("secrets", secret.toString(), fabric8SecretsPropertySourceLocator,
				environment, Fabric8SecretsPropertySource.class);
		if (reload) {
			reloadProperties();
		}

	}

	private final class SecretInformerAwareEventHandler implements ResourceEventHandler<Secret> {

		private final SharedIndexInformer<Secret> informer;

		private SecretInformerAwareEventHandler(SharedIndexInformer<Secret> informer) {
			this.informer = informer;
		}

		@Override
		public void onAdd(Secret secret) {
			LOG.debug("Secret " + secret.getMetadata().getName() + " was added.");
			onEvent(secret);
		}

		@Override
		public void onUpdate(Secret oldSecret, Secret newSecret) {
			LOG.debug("Secret " + newSecret.getMetadata().getName() + " was updated.");
			onEvent(newSecret);
		}

		@Override
		public void onDelete(Secret secret, boolean deletedFinalStateUnknown) {
			LOG.debug("Secret " + secret.getMetadata().getName() + " was deleted.");
			onEvent(secret);
		}

		@Override
		public void onNothing() {
			List<Secret> store = informer.getStore().list();
			LOG.info("onNothing called with a store of size : " + store.size());
			LOG.info("this might be an indication of a HTTP_GONE code");
		}

	}

}
