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

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.CallGeneratorParams;
import jakarta.annotation.PostConstruct;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationChangeDetector;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.createApiClientForInformerClient;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedSecretsChangeDetector extends ConfigurationChangeDetector {

	private final CoreV1Api coreV1Api;

	private final KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

	private final SharedInformerFactory factory;

	private final String namespace;

	private final boolean monitorSecrets;

	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		// We need to pass an APIClient to the SharedInformerFactory because if we use the
		// default constructor it will use the configured default APIClient but that may
		// not
		// contain an APIClient configured within the cluster and does not contain the
		// necessary
		// certificate authorities for the cluster. This results in SSL errors.
		// See https://github.com/spring-cloud/spring-cloud-kubernetes/issues/885
		this.factory = new SharedInformerFactory(createApiClientForInformerClient());
		this.coreV1Api = coreV1Api;
		this.namespace = kubernetesNamespaceProvider.getNamespace();
		this.monitorSecrets = properties.isMonitoringSecrets();
	}

	@PostConstruct
	public void watch() {
		if (coreV1Api != null && monitorSecrets) {
			SharedIndexInformer<V1Secret> configMapInformer = factory.sharedIndexInformerFor(
					(CallGeneratorParams params) -> coreV1Api.listNamespacedSecretCall(namespace, null, null, null,
							null, null, null, params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
					V1Secret.class, V1SecretList.class);
			configMapInformer.addEventHandler(new ResourceEventHandler<>() {
				@Override
				public void onAdd(V1Secret obj) {
					log.info("Secret " + obj.getMetadata().getName() + " was added.");
					onEvent(obj);
				}

				@Override
				public void onUpdate(V1Secret oldObj, V1Secret newObj) {
					log.info("Secret " + newObj.getMetadata().getName() + " was added.");
					onEvent(newObj);
				}

				@Override
				public void onDelete(V1Secret obj, boolean deletedFinalStateUnknown) {
					log.info("Secret " + obj.getMetadata() + " was deleted.");
					onEvent(obj);
				}
			});
			factory.startAllRegisteredInformers();
		}
	}

	private void onEvent(V1Secret secret) {
		log.debug("onEvent configMap: " + secret.toString());
		boolean changed = changed(locateMapPropertySources(this.propertySourceLocator, this.environment),
				findPropertySources(KubernetesClientSecretsPropertySource.class));
		if (changed) {
			log.info("Detected change in secrets");
			reloadProperties();
		}
	}

}
