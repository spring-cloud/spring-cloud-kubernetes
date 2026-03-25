/*
 * Copyright 2013-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Collections;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.Service;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.DiscoveryClientConfigServiceBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Zhanwei Wang
 */
class Fabric8DiscoveryClientConfigClientBootstrapConfigurationTests {

	private AnnotationConfigApplicationContext context;

	@AfterEach
	void afterEach() {
		if (context != null) {
			if (context.getParent() != null) {
				((AnnotationConfigApplicationContext) context.getParent()).close();
			}
			context.close();
		}
	}

	@Test
	void onWhenRequested() {
		setup("server.port=7000", "spring.cloud.config.discovery.enabled=true",
				"spring.cloud.kubernetes.discovery.enabled:true", "spring.application.name:test",
				"spring.cloud.config.discovery.service-id:configserver");
		Assertions.assertThat(context.getParent().getBeanNamesForType(DiscoveryClient.class).length).isEqualTo(1);
		DiscoveryClient client = context.getParent().getBean(DiscoveryClient.class);
		verify(client, atLeast(2)).getInstances("configserver");
		ConfigClientProperties locator = context.getBean(ConfigClientProperties.class);
		Assertions.assertThat(locator.getUri()[0]).isEqualTo("http://fake:8888/");
	}

	/**
	 * When using config-first bootstrap with Fabric8 Kubernetes discovery, the bootstrap context
	 * must include {@link Fabric8DiscoveryClientSpelAutoConfiguration} so that the
	 * required {@code Predicate<Service>} bean is available for
	 * {@link Fabric8DiscoveryClientAutoConfiguration}.
	 */
	@Test
	void spelPredicateBeanAvailableInBootstrapContext() {
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
		TestPropertyValues
			.of("spring.cloud.config.discovery.enabled=true", "spring.main.cloud-platform=KUBERNETES",
					"spring.cloud.kubernetes.discovery.enabled=true")
			.applyTo(parent);
		parent.register(PropertyPlaceholderAutoConfiguration.class, KubernetesCommonsAutoConfiguration.class,
				Fabric8DiscoveryClientConfigClientBootstrapConfiguration.class);
		parent.refresh();

		context = new AnnotationConfigApplicationContext();
		context.setParent(parent);
		context.refresh();

		ResolvableType predicateType = ResolvableType.forClassWithGenerics(Predicate.class, Service.class);
		Assertions.assertThat(parent.getBeanNamesForType(predicateType)).isNotEmpty();
	}

	private void setup(String... env) {
		AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
		TestPropertyValues.of(env).applyTo(parent);
		parent.register(UtilAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
				EnvironmentKnobbler.class, KubernetesCommonsAutoConfiguration.class,
				Fabric8DiscoveryClientConfigClientBootstrapConfiguration.class,
				DiscoveryClientConfigServiceBootstrapConfiguration.class, ConfigClientProperties.class);
		parent.refresh();
		context = new AnnotationConfigApplicationContext();
		context.setParent(parent);
		context.register(PropertyPlaceholderAutoConfiguration.class, KubernetesCommonsAutoConfiguration.class,
				Fabric8DiscoveryClientAutoConfiguration.class);
		context.refresh();
	}

	@Configuration(proxyBeanMethods = false)
	protected static class EnvironmentKnobbler {

		@Bean
		Fabric8DiscoveryClient kubernetesDiscoveryClient() {
			Fabric8DiscoveryClient client = mock(Fabric8DiscoveryClient.class);
			ServiceInstance instance = new DefaultServiceInstance("configserver1", "configserver", "fake", 8888, false);
			given(client.getInstances("configserver")).willReturn(Collections.singletonList(instance));
			return client;
		}

	}

}
