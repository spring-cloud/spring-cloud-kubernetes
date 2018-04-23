package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

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
