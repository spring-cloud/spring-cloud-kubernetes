/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.loadbalancer;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesLoadBalancerPropertiesTests {

	@Test
	void testBindingWhenNoPropertiesProvided() {
		new ApplicationContextRunner().withUserConfiguration(KubernetesLoadBalancerPropertiesTests.Config.class)
			.run(context -> {
				KubernetesLoadBalancerProperties props = context.getBean(KubernetesLoadBalancerProperties.class);
				assertThat(props).isNotNull();
				assertThat(props.getEnabled()).isTrue();
				assertThat(props.getMode()).isEqualTo(KubernetesLoadBalancerMode.POD);
				assertThat(props.getClusterDomain()).isEqualTo("cluster.local");
				assertThat(props.getPortName()).isEqualTo("http");
			});
	}

	@Test
	void testBindingWhenSomePropertiesProvided() {
		new ApplicationContextRunner().withUserConfiguration(KubernetesLoadBalancerPropertiesTests.Config.class)
			.withPropertyValues("spring.cloud.kubernetes.loadbalancer.enabled=false",
					"spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
					"spring.cloud.kubernetes.loadbalancer.clusterDomain=clusterDomain",
					"spring.cloud.kubernetes.loadbalancer.portName=portName")
			.run(context -> {
				KubernetesLoadBalancerProperties props = context.getBean(KubernetesLoadBalancerProperties.class);
				assertThat(props).isNotNull();
				assertThat(props.getEnabled()).isFalse();
				assertThat(props.getMode()).isEqualTo(KubernetesLoadBalancerMode.SERVICE);
				assertThat(props.getClusterDomain()).isEqualTo("clusterDomain");
				assertThat(props.getPortName()).isEqualTo("portName");
			});
	}

	@Configuration
	@EnableConfigurationProperties(KubernetesLoadBalancerProperties.class)
	static class Config {

	}

}
