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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
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

	private final ApiClient apiClient;

	private final List<SharedIndexInformer<V1Secret>> informers = new ArrayList<>();

	private final List<SharedInformerFactory> factories = new ArrayList<>();

	private final Set<String> namespaces;

	private final boolean enableReloadFiltering;

	private final ResourceEventHandler<V1Secret> handler = new ResourceEventHandler<>() {

		@Override
		public void onAdd(V1Secret secret) {
			LOG.debug(() -> "Secret " + secret.getMetadata().getName() + " was added in namespace "
					+ secret.getMetadata().getNamespace());
			onEvent(secret);
		}

		@Override
		public void onUpdate(V1Secret oldSecret, V1Secret newSecret) {
			LOG.debug(() -> "Secret " + newSecret.getMetadata().getName() + " was updated in namespace "
					+ newSecret.getMetadata().getNamespace());

			if (KubernetesClientEventBasedSecretsChangeDetector.equals(oldSecret.getData(), newSecret.getData())) {
				LOG.debug(() -> "data in secret has not changed, will not reload");
			}
			else {
				onEvent(newSecret);
			}
		}

		@Override
		public void onDelete(V1Secret secret, boolean deletedFinalStateUnknown) {
			LOG.debug(() -> "Secret " + secret.getMetadata().getName() + " was deleted in namespace "
					+ secret.getMetadata().getNamespace());
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
		this.apiClient = createApiClientForInformerClient();
		this.enableReloadFiltering = properties.enableReloadFiltering();
		namespaces = namespaces(kubernetesNamespaceProvider, properties, "secret");
	}

	@PostConstruct
	void inform() {
		LOG.info(() -> "Kubernetes event-based secrets change detector activated");

		namespaces.forEach(namespace -> {
			SharedIndexInformer<V1Secret> informer;
			String[] filter = new String[1];

			if (enableReloadFiltering) {
				filter[0] = ConfigReloadProperties.RELOAD_LABEL_FILTER + "=true";
			}
			SharedInformerFactory factory = new SharedInformerFactory(apiClient);
			factories.add(factory);
			informer = factory.sharedIndexInformerFor(
					(CallGeneratorParams params) -> coreV1Api.listNamespacedSecretCall(namespace, null, null, null,
							null, filter[0], null, params.resourceVersion, null, null, params.timeoutSeconds,
							params.watch, null),
					V1Secret.class, V1SecretList.class);

			LOG.debug(() -> "added secret informer for namespace : " + namespace + " with filter : " + filter[0]);

			informer.addEventHandler(handler);
			informers.add(informer);
			factory.startAllRegisteredInformers();
		});

	}

	@PreDestroy
	void shutdown() {
		informers.forEach(SharedIndexInformer::stop);
		factories.forEach(SharedInformerFactory::stopAllRegisteredInformers);
	}

	protected void onEvent(KubernetesObject secret) {
		boolean reload = ConfigReloadUtil.reload("secrets", secret.toString(), propertySourceLocator, environment,
				KubernetesClientSecretsPropertySource.class);
		if (reload) {
			reloadProperties();
		}
	}

	static boolean equals(Map<String, byte[]> left, Map<String, byte[]> right) {
		Map<String, byte[]> innerLeft = Optional.ofNullable(left).orElse(Map.of());
		Map<String, byte[]> innerRight = Optional.ofNullable(right).orElse(Map.of());

		if (innerLeft.size() != innerRight.size()) {
			return false;
		}

		for (Map.Entry<String, byte[]> entry : innerLeft.entrySet()) {
			String key = entry.getKey();
			byte[] value = entry.getValue();
			if (!Arrays.equals(value, innerRight.get(key))) {
				return false;
			}
		}
		return true;
	}

}
