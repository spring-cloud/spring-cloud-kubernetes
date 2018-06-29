package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Ryan Dawson
 */
public class KubernetesDiscoveryConfigurationTest {

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
		assertFalse(context.containsBean("discoveryClient"));
	}

	@Test
	public void kubernetesDiscoveryWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false");
		assertFalse(context.containsBean("discoveryClient"));
	}


	@Test
	public void kubernetesDiscoveryDefaultEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true");
		assertTrue(context.containsBean("discoveryClient"));
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(
			PropertyPlaceholderAutoConfiguration.class,
			KubernetesClientTestConfiguration.class,
			KubernetesDiscoveryClientAutoConfiguration.class).web(false)
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
