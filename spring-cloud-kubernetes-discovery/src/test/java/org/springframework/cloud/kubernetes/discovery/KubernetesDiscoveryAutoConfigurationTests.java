package org.springframework.cloud.kubernetes.discovery;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.DiscoveryClientConfigServiceBootstrapConfiguration;
import org.springframework.cloud.kubernetes.KubernetesAutoConfiguration;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Zhanwei Wang
 */
@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({ "spring-retry-*.jar", "spring-boot-starter-aop-*.jar" })
public class KubernetesDiscoveryAutoConfigurationTests {
	private AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			if (this.context.getParent() != null) {
				((AnnotationConfigApplicationContext) this.context.getParent()).close();
			}
			this.context.close();
		}
	}

	@Test
	public void onWhenRequested() throws Exception {
		setup("server.port=7000", "spring.cloud.config.discovery.enabled=true",
			"spring.cloud.kubernetes.discovery.enabled:true",
			"spring.cloud.kubernetes.enabled:true",
			"spring.application.name:test",
			"spring.cloud.config.discovery.service-id:configserver");
		assertEquals( 1, this.context.getParent()
			.getBeanNamesForType(DiscoveryClient.class).length);
		DiscoveryClient client = this.context.getParent().getBean(
			DiscoveryClient.class);
		verify(client, atLeast(2)).getInstances("configserver");
		ConfigClientProperties locator = this.context
			.getBean(ConfigClientProperties.class);
		assertEquals("http://fake:8888/", locator.getUri()[0]);
	}

	private void setup(String... env) {
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
		TestPropertyValues.of(env).applyTo(parent);
		parent.register(UtilAutoConfiguration.class,
			PropertyPlaceholderAutoConfiguration.class, EnvironmentKnobbler.class,
			KubernetesDiscoveryClientBootstrapConfiguration.class,
			DiscoveryClientConfigServiceBootstrapConfiguration.class,
			ConfigClientProperties.class);
		parent.refresh();
		this.context = new AnnotationConfigApplicationContext();
		this.context.setParent(parent);
		this.context.register(PropertyPlaceholderAutoConfiguration.class,
			KubernetesAutoConfiguration.class,
			KubernetesDiscoveryClientAutoConfiguration.class);
		this.context.refresh();
	}

	@Configuration
	protected static class EnvironmentKnobbler {

		@Bean
		public DiscoveryClient kubernetesDiscoveryClient(
			KubernetesDiscoveryProperties properties) {
			KubernetesDiscoveryClient client = mock(KubernetesDiscoveryClient.class);
			ServiceInstance instance = new DefaultServiceInstance("configserver",
				"fake", 8888, false);
			given(client.getInstances("configserver"))
				.willReturn(Arrays.asList(instance));
			return client;
		}
	}
}
