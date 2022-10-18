/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.Set;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.CallGeneratorParams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.createApiClientForInformerClient;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.namespaces;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedSecretsChangeDetector extends ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientEventBasedSecretsChangeDetector.class));

	private final CoreV1Api coreV1Api;

	private final KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

	private final boolean monitorSecrets;

	private final SharedInformerFactory factory;

	private final List<SharedIndexInformer<V1Secret>> informers = new ArrayList<>();

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	private final ResourceEventHandler<V1Secret> handler = new ResourceEventHandler<>() {

		@Override
		public void onAdd(V1Secret secret) {
			LOG.debug(() -> "Secret " + secret.getMetadata().getName() + " was added.");
			onEvent(secret);
		}

		@Override
		public void onUpdate(V1Secret oldSecret, V1Secret newSecret) {
			LOG.debug(() -> "Secret " + newSecret.getMetadata().getName() + " was updated.");
			onEvent(newSecret);
		}

		@Override
		public void onDelete(V1Secret secret, boolean deletedFinalStateUnknown) {
			LOG.debug(() -> "Secret " + secret.getMetadata().getName() + " was deleted.");
			onEvent(secret);
		}
	};

	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.coreV1Api = coreV1Api;
		// We need to pass an APIClient to the SharedInformerFactory because if we use the
		// default
		// constructor it will use the configured default APIClient but that may not
		// contain
		// an APIClient configured within the cluster and does not contain the necessary
		// certificate authorities for the cluster. This results in SSL errors.
		// See https://github.com/spring-cloud/spring-cloud-kubernetes/issues/885
		this.factory = new SharedInformerFactory(createApiClientForInformerClient());
		this.monitorSecrets = properties.monitoringSecrets();
		this.enableReloadFiltering = properties.enableReloadFiltering();
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "secret");
	}

	@PostConstruct
	void inform() {
		if (monitorSecrets) {
			LOG.info(() -> "Kubernetes event-based secrets change detector activated");

			namespaces.forEach(namespace -> {
				SharedIndexInformer<V1Secret> informer;
				String filter = null;

				if (enableReloadFiltering) {
					filter = ConfigReloadProperties.RELOAD_LABEL_FILTER + "=true";
					LOG.debug(() -> "added secret informer for namespace : " + namespace + " with enabled filter");
				}
				else {
					LOG.debug(() -> "added secret informer for namespace : " + namespace);
				}

				String filterOnInformerLabel = filter;
				informer = factory.sharedIndexInformerFor(
						(CallGeneratorParams params) -> coreV1Api.listNamespacedSecretCall(namespace, null, null, null,
								null, filterOnInformerLabel, null, params.resourceVersion, null, params.timeoutSeconds,
								params.watch, null),
						V1Secret.class, V1SecretList.class);
				informer.addEventHandler(handler);
				informers.add(informer);
			});

			factory.startAllRegisteredInformers();
		}
	}

	@PreDestroy
	void shutdown() {
		informers.forEach(SharedIndexInformer::stop);
		factory.stopAllRegisteredInformers();
	}

	protected void onEvent(KubernetesObject secret) {
		boolean reload = ConfigReloadUtil.reload("secrets", secret.toString(), propertySourceLocator, environment,
				KubernetesClientSecretsPropertySource.class);
		if (reload) {
			reloadProperties();
		}
	}

}
