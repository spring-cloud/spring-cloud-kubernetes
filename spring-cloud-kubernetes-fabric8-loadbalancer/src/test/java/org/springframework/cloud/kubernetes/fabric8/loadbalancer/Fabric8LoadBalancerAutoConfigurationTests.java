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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Thomas Vitale
 */
class Fabric8LoadBalancerAutoConfigurationTests {

	@Test
	void kubernetesLoadBalancerWhenKubernetesDisabledAndLoadBalancerDisabled() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LoadBalancerAutoConfigurationTests.Config.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.cloud.kubernetes.loadbalancer.enabled=false")
			.run(this::assertInstanceMapperMissing);
	}

	@Test
	void kubernetesLoadBalancerWhenKubernetesDisabledAndLoadBalancerEnabled() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LoadBalancerAutoConfigurationTests.Config.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.cloud.kubernetes.loadbalancer.enabled=true")
			.run(this::assertInstanceMapperMissing);
	}

	@Test
	void kubernetesLoadBalancerWhenKubernetesEnabledAndLoadBalancerEnabled() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LoadBalancerAutoConfigurationTests.Config.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.cloud.kubernetes.loadbalancer.enabled=true",
					"spring.main.cloud-platform=KUBERNETES")
			.run(this::assertInstanceMapperPresent);
	}

	@Test
	void kubernetesLoadBalancerWhenKubernetesEnabledAndLoadBalancerDisabled() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LoadBalancerAutoConfigurationTests.Config.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.cloud.kubernetes.loadbalancer.enabled=false",
					"spring.main.cloud-platform=KUBERNETES")
			.run(this::assertInstanceMapperMissing);
	}

	@Test
	void kubernetesLoadBalancerWhenDefaultProperties() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LoadBalancerAutoConfigurationTests.Config.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=KUBERNETES")
			.run(this::assertInstanceMapperPresent);
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.loadbalancer.service-matching-strategy is not set
	 *
	 *     - we must default to the named strategy
	 * </pre>
	 */
	@Test
	void testNoExplicitServiceMatchingStrategy() {
		new ApplicationContextRunner()
			.withUserConfiguration(Config.class, Fabric8LoadBalancerClientConfiguration.class, TestConfig.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=KUBERNETES",
					"spring.cloud.kubernetes.loadbalancer.mode=SERVICE")
			.run(context -> {
				assertThat(context).hasSingleBean(ServiceInstanceListSupplier.class);
				assertThat(context).hasBean("kubernetesNameBasedServicesListSupplier");
				assertThat(context).doesNotHaveBean("kubernetesLabelsBasedServicesListSupplier");
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.loadbalancer.service-matching-strategy=NAME
	 *
	 *     - we must use the named strategy
	 * </pre>
	 */
	@Test
	void testNameExplicitServiceMatchingStrategy() {
		new ApplicationContextRunner()
			.withUserConfiguration(Config.class, Fabric8LoadBalancerClientConfiguration.class, TestConfig.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=KUBERNETES",
					"spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
					"spring.cloud.kubernetes.loadbalancer.service-matching-strategy=NAME")
			.run(context -> {
				assertThat(context).hasSingleBean(ServiceInstanceListSupplier.class);
				assertThat(context).hasBean("kubernetesNameBasedServicesListSupplier");
				assertThat(context).doesNotHaveBean("kubernetesLabelsBasedServicesListSupplier");
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.loadbalancer.service-matching-strategy=LABELS
	 *
	 *     - we must use the labels strategy
	 * </pre>
	 */
	@Test
	void testLabelsExplicitServiceMatchingStrategy() {
		new ApplicationContextRunner()
			.withUserConfiguration(Config.class, Fabric8LoadBalancerClientConfiguration.class, TestConfig.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LoadBalancerAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=KUBERNETES",
					"spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
					"spring.cloud.kubernetes.loadbalancer.service-matching-strategy=LABELS")
			.run(context -> {
				assertThat(context).hasSingleBean(ServiceInstanceListSupplier.class);
				assertThat(context).doesNotHaveBean("kubernetesNameBasedServicesListSupplier");
				assertThat(context).hasBean("kubernetesLabelsBasedServicesListSupplier");
			});
	}

	private void assertInstanceMapperMissing(AssertableApplicationContext context) {
		assertThat(context.getBeanNamesForType(Fabric8ServiceInstanceMapper.class)).isEmpty();
	}

	private void assertInstanceMapperPresent(AssertableApplicationContext context) {
		assertThat(context.getBeanNamesForType(Fabric8ServiceInstanceMapper.class)).hasSize(1);
	}

	@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
	static class Config {

	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		KubernetesClient kubernetesClient() {
			return Mockito.mock(KubernetesClient.class);
		}

	}

}
