package org.springframework.cloud.kubernetes.config;

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
public class KubernetesConfigConfigurationTest {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}


	@Test
	public void kubernetesWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false");
		assertFalse(context.containsBean("configMapPropertySourceLocator"));
		assertFalse(context.containsBean("secretsPropertySourceLocator"));
	}

	@Test
	public void kubernetesWhenKubernetesConfigDisabled() throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=false");
		assertFalse(context.containsBean("configMapPropertySourceLocator"));
		assertFalse(context.containsBean("secretsPropertySourceLocator"));
	}


	@Test
	public void kubernetesDefaultEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true");
		assertTrue(context.containsBean("configMapPropertySourceLocator"));
		assertTrue(context.containsBean("secretsPropertySourceLocator"));
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(
			PropertyPlaceholderAutoConfiguration.class,
			KubernetesClientTestConfiguration.class,
			BootstrapConfiguration.class).web(false)
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
