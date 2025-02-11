package org.springframework.cloud.kubernetes.configserver;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.stereotype.Component;

/**
 * Factory class for creating instances of {@link KubernetesEnvironmentRepository}.
 */
@Component
public class KubernetesEnvironmentRepositoryFactory
	implements EnvironmentRepositoryFactory<KubernetesEnvironmentRepository, KubernetesConfigServerProperties> {

	private final ObjectProvider<KubernetesEnvironmentRepository> kubernetesEnvironmentRepositoryProvider;

	public KubernetesEnvironmentRepositoryFactory(ObjectProvider<KubernetesEnvironmentRepository> kubernetesEnvironmentRepositoryProvider) {
		this.kubernetesEnvironmentRepositoryProvider = kubernetesEnvironmentRepositoryProvider;
	}

	@Override
	public KubernetesEnvironmentRepository build(KubernetesConfigServerProperties environmentProperties) {
		return kubernetesEnvironmentRepositoryProvider.getIfAvailable();
	}
}
