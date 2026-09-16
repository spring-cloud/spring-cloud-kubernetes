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

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PostConstruct;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
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
public class Fabric8EventBasedSecretsChangeDetector extends Fabric8EventBasedChangeDetector<Secret> {

	private static final LogAccessor LOG = new LogAccessor(Fabric8EventBasedSecretsChangeDetector.class);

	private final Set<String> namespaces;

	private final boolean monitorSecrets;

	private final Map<String, String> secretsLabels;

	public Fabric8EventBasedSecretsChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			Fabric8SecretsPropertySourceLocator fabric8SecretsPropertySourceLocator,
			KubernetesNamespaceProvider namespaceProvider) {
		super(environment, kubernetesClient, strategy, fabric8SecretsPropertySourceLocator,
				Fabric8SecretsPropertySource.class);
		this.monitorSecrets = properties.monitoringSecrets();
		secretsLabels = properties.secretsLabels();
		namespaces = namespaces(kubernetesClient, namespaceProvider, properties, "secrets");
	}

	@PostConstruct
	private void inform() {
		if (monitorSecrets) {

			LOG.info("Kubernetes event-based secrets change detector activated");

			namespaces.forEach(namespace -> {
				SharedIndexInformer<Secret> informer;
				informer = kubernetesClient.secrets().inNamespace(namespace).withLabels(secretsLabels).inform();
				LOG.debug("added secret informer for namespace : " + namespace + " with labels : " + secretsLabels);

				informer.addEventHandler(new Fabric8ResourceEventHandler<>(informer, this::onEvent));
				informers.add(informer);
			});
		}
		else {
			LOG.debug("Kubernetes event-based secrets change detector deactivated");
		}
	}

}
