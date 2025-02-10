package org.springframework.cloud.kubernetes.configserver;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
		String combinedNamespaces = Stream.of(environmentProperties.getSecretsNamespaces(), environmentProperties.getConfigMapNamespaces())
			.filter(ns -> ns != null && !ns.isEmpty())
			.collect(Collectors.joining(","));
		return new KubernetesEnvironmentRepository(coreV1Api, kubernetesPropertySourceSuppliers, combinedNamespaces);
	}
}
