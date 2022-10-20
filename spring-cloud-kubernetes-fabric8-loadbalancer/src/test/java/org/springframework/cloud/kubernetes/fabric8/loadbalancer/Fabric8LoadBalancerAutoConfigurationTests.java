/*
 * Copyright 2013-2020 the original author or authors.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Thomas Vitale
 */
class Fabric8LoadBalancerAutoConfigurationTests {

	private ConfigurableApplicationContext context;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void kubernetesLoadBalancerWhenKubernetesDisabledAndLoadBalancerDisabled() {
		setup("spring.cloud.kubernetes.loadbalancer.enabled=false");
		assertThat(this.context.getBeanNamesForType(Fabric8ServiceInstanceMapper.class)).isEmpty();
	}

	@Test
	void kubernetesLoadBalancerWhenKubernetesDisabledAndLoadBalancerEnabled() {
		setup("spring.cloud.kubernetes.loadbalancer.enabled=true");
		assertThat(this.context.getBeanNamesForType(Fabric8ServiceInstanceMapper.class)).isEmpty();
	}

	@Test
	void kubernetesLoadBalancerWhenKubernetesEnabledAndLoadBalancerEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.loadbalancer.enabled=true");
		assertThat(this.context.getBeanNamesForType(Fabric8ServiceInstanceMapper.class)).hasSize(1);
	}

	@Test
	void kubernetesLoadBalancerWhenKubernetesEnabledAndLoadBalancerDisabled() {
		setup("spring.cloud.kubernetes.loadbalancer.enabled=false");
		assertThat(this.context.getBeanNamesForType(Fabric8ServiceInstanceMapper.class)).isEmpty();
	}

	@Test
	void kubernetesLoadBalancerWhenDefaultProperties() {
		setup("spring.main.cloud-platform=KUBERNETES");
		assertThat(this.context.getBeanNamesForType(Fabric8ServiceInstanceMapper.class)).hasSize(1);
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(Fabric8LoadBalancerAutoConfiguration.class, Config.class)
				.web(org.springframework.boot.WebApplicationType.NONE).properties(env).run();
	}

	@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
	static class Config {

	}

}
