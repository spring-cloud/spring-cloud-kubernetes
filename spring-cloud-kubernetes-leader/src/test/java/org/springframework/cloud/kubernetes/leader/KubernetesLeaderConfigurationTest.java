package org.springframework.cloud.kubernetes.leader;

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
public class KubernetesLeaderConfigurationTest {

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
		assertFalse(context.containsBean("defaultLeaderEventPublisher"));
		assertFalse(context.containsBean("leaderKubernetesHelper"));
		assertFalse(context.containsBean("leadershipController"));
		assertFalse(context.containsBean("leaderInitiator"));
	}

	@Test
	public void kubernetesWhenKubernetesLeaderDisabled() throws Exception {
		setup("spring.cloud.kubernetes.leader.enabled=false");
		assertFalse(context.containsBean("defaultLeaderEventPublisher"));
		assertFalse(context.containsBean("leaderKubernetesHelper"));
		assertFalse(context.containsBean("leadershipController"));
		assertFalse(context.containsBean("leaderInitiator"));
	}


	@Test
	public void kubernetesDefaultEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true");
		assertTrue(context.containsBean("defaultLeaderEventPublisher"));
		assertTrue(context.containsBean("leaderKubernetesHelper"));
		assertTrue(context.containsBean("leadershipController"));
		assertTrue(context.containsBean("leaderInitiator"));
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(
			PropertyPlaceholderAutoConfiguration.class,
			KubernetesClientTestConfiguration.class,
			LeaderAutoConfiguration.class).web(org.springframework.boot.WebApplicationType.NONE)
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
