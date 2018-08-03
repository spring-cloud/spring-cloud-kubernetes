package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Oleg Vyukov
 */
public class KubernetesCatalogServicesWatchConfigurationTest {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void kubernetesCatalogWatchDisabled() throws Exception {
		setup("spring.cloud.kubernetes.discovery.catalog-services-watch.enabled=false");
		assertFalse(context.containsBean("kubernetesCatalogWatch"));
	}

	@Test
	public void kubernetesCatalogWatchDefaultEnabled() throws Exception {
		setup();
		assertTrue(context.containsBean("kubernetesCatalogWatch"));
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(
			PropertyPlaceholderAutoConfiguration.class,
			KubernetesClientTestConfiguration.class,
			KubernetesDiscoveryClientAutoConfiguration.class).web(WebApplicationType.NONE)
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
