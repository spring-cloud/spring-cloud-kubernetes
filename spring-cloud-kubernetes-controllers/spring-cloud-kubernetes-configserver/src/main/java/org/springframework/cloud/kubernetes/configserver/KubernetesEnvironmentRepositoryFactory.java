package org.springframework.cloud.kubernetes.configserver;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Factory for creating {@link KubernetesEnvironmentRepository} instances.
 */
public class KubernetesEnvironmentRepositoryFactory
	implements EnvironmentRepositoryFactory<KubernetesEnvironmentRepository, KubernetesConfigServerProperties> {

	private final List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSupplierList;

	public KubernetesEnvironmentRepositoryFactory(
		List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSupplierList
	) {
		this.kubernetesPropertySourceSupplierList = kubernetesPropertySourceSupplierList;
	}

	@Override
	public KubernetesEnvironmentRepository build(KubernetesConfigServerProperties environmentProperties) {
		CoreV1Api coreApi = new CoreV1Api();
		String namespace = environmentProperties.getSecretsNamespaces() != null ? environmentProperties.getSecretsNamespaces() : "default";
		return new KubernetesEnvironmentRepository(coreApi, this.kubernetesPropertySourceSupplierList, namespace);
	}

}
