package org.springframework.cloud.kubernetes.configserver;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KubernetesEnvironmentRepositoryFactory
	implements EnvironmentRepositoryFactory<KubernetesEnvironmentRepository, KubernetesConfigServerProperties> {

	private final CoreV1Api coreV1Api;
	private final List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSuppliers;

	public KubernetesEnvironmentRepositoryFactory(CoreV1Api coreV1Api,
												  List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSuppliers) {
		this.coreV1Api = coreV1Api;
		this.kubernetesPropertySourceSuppliers = kubernetesPropertySourceSuppliers;
	}

	@Override
	public KubernetesEnvironmentRepository build(KubernetesConfigServerProperties environmentProperties) {
		return new KubernetesEnvironmentRepository(coreV1Api, kubernetesPropertySourceSuppliers, "default");
	}
}
