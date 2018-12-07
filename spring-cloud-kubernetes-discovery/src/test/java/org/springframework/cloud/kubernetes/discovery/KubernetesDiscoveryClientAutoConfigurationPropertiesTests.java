package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.After;
import org.junit.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Ryan Dawson
 */
public class KubernetesDiscoveryClientAutoConfigurationPropertiesTests {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void kubernetesDiscoveryDisabled() throws Exception {
		setup("spring.cloud.kubernetes.discovery.enabled=false","spring.cloud.kubernetes.discovery.catalog-services-watch.enabled=false");
		assertThat(context.getBeanNamesForType(KubernetesDiscoveryClient.class))
			.isEmpty();
	}

	@Test
	public void kubernetesDiscoveryWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false");
		assertThat(context.getBeanNamesForType(KubernetesDiscoveryClient.class))
			.isEmpty();
	}


	@Test
	public void kubernetesDiscoveryDefaultEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true");
		assertThat(context.getBeanNamesForType(KubernetesDiscoveryClient.class))
			.hasSize(1);
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(
			PropertyPlaceholderAutoConfiguration.class,
			KubernetesClientTestConfiguration.class,
			KubernetesDiscoveryClientAutoConfiguration.class).web(org.springframework.boot.WebApplicationType.NONE)
			.properties(env).run();
	}


	@Configuration
	static class KubernetesClientTestConfiguration {

		@Bean
		KubernetesClient kubernetesClient() {
			return mock(KubernetesClient.class);
		}


	}
}
