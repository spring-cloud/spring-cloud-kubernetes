package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.registry.KubernetesRegistration;
import org.springframework.cloud.kubernetes.registry.KubernetesServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesDiscoveryClientConfig {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClientConfig.class);

	@Bean
	public DiscoveryClient discoveryClient(KubernetesClient client,
										   KubernetesDiscoveryProperties properties) {
		return new KubernetesDiscoveryClient(client,
											 properties);
	}

	@Bean
	public KubernetesServiceRegistry getServiceRegistry() {
		return new KubernetesServiceRegistry();
	}

	@Bean
	public KubernetesRegistration getRegistration(KubernetesClient client,
												  KubernetesDiscoveryProperties properties) {
		return new KubernetesRegistration(client,
										  properties);
	}

	@Bean
	public KubernetesDiscoveryProperties getProperties() {
		return new KubernetesDiscoveryProperties();
	}
}
