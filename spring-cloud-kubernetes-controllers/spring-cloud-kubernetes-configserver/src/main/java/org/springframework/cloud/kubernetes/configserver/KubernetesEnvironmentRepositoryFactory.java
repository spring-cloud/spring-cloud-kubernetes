package org.springframework.cloud.kubernetes.configserver;

import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.stereotype.Component;

@Component
public class KubernetesEnvironmentRepositoryFactory
	implements EnvironmentRepositoryFactory<KubernetesEnvironmentRepository, KubernetesConfigServerProperties> {

	private final KubernetesEnvironmentRepository kubernetesEnvironmentRepository;

	public KubernetesEnvironmentRepositoryFactory(KubernetesEnvironmentRepository kubernetesEnvironmentRepository) {
		this.kubernetesEnvironmentRepository = kubernetesEnvironmentRepository;
	}

	@Override
	public KubernetesEnvironmentRepository build(KubernetesConfigServerProperties environmentProperties) {
		return kubernetesEnvironmentRepository;
	}
}
