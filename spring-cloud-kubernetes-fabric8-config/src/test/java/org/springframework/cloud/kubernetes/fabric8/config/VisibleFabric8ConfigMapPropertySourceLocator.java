package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;

public class VisibleFabric8ConfigMapPropertySourceLocator extends Fabric8ConfigMapPropertySourceLocator {

	public VisibleFabric8ConfigMapPropertySourceLocator(KubernetesClient client, ConfigMapConfigProperties properties, KubernetesNamespaceProvider provider) {
		super(client, properties, provider);
	}
}
