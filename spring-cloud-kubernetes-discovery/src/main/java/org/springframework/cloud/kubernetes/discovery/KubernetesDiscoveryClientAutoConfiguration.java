package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.registry.KubernetesRegistration;
import org.springframework.cloud.kubernetes.registry.KubernetesServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class KubernetesDiscoveryClientAutoConfiguration {

	@Bean
	public DiscoveryClient discoveryClient(KubernetesClient client,
										   KubernetesDiscoveryProperties properties) {
		return new KubernetesDiscoveryClient(client, properties);
	}

	@Bean
	public KubernetesServiceRegistry getServiceRegistry() {
		return new KubernetesServiceRegistry();
	}

	@Bean
	public KubernetesRegistration getRegistration(KubernetesClient client,
												  KubernetesDiscoveryProperties properties) {
		return new KubernetesRegistration(client, properties);
	}

	@Bean
	@Primary
	public KubernetesDiscoveryProperties getKubernetesDiscoveryProperties() {
		return new KubernetesDiscoveryProperties();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.cloud.kubernetes.discovery.catalog-services-watch.enabled", matchIfMissing = true)
	public KubernetesCatalogWatch kubernetesCatalogWatch(KubernetesClient client) {
		return new KubernetesCatalogWatch(client);
	}
}
