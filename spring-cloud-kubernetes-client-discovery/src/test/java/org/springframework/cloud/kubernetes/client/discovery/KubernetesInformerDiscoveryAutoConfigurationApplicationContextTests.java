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

package org.springframework.cloud.kubernetes.client.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Test various conditionals for {@link KubernetesInformerDiscoveryAutoConfiguration}
 *
 * @author wind57
 */
class KubernetesInformerDiscoveryAutoConfigurationApplicationContextTests {

	private ConfigurableApplicationContext context;

	@AfterEach
	void close() {
		context.close();
	}

	@Test
	void discoveryEnabledDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false");
		Assertions.assertEquals(context.getBeanNamesForType(SpringCloudKubernetesInformerFactoryProcessor.class).length,
				1);
		Assertions.assertEquals(context.getBeanNamesForType(KubernetesInformerDiscoveryClient.class).length, 1);
	}

	@Test
	void discoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=true");
		Assertions.assertEquals(context.getBeanNamesForType(SpringCloudKubernetesInformerFactoryProcessor.class).length,
				1);
		Assertions.assertEquals(context.getBeanNamesForType(KubernetesInformerDiscoveryClient.class).length, 1);
	}

	@Test
	void discoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.enabled=false");
		Assertions.assertEquals(context.getBeanNamesForType(SpringCloudKubernetesInformerFactoryProcessor.class).length,
				0);
		Assertions.assertEquals(context.getBeanNamesForType(KubernetesInformerDiscoveryClient.class).length, 0);
	}

	@Test
	void kubernetesDiscoveryEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=true");
		Assertions.assertEquals(context.getBeanNamesForType(SpringCloudKubernetesInformerFactoryProcessor.class).length,
				1);
		Assertions.assertEquals(context.getBeanNamesForType(KubernetesInformerDiscoveryClient.class).length, 1);
	}

	@Test
	void kubernetesDiscoveryDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.enabled=false");
		Assertions.assertEquals(context.getBeanNamesForType(SpringCloudKubernetesInformerFactoryProcessor.class).length,
				0);
		Assertions.assertEquals(context.getBeanNamesForType(KubernetesInformerDiscoveryClient.class).length, 0);
	}

	@Test
	void kubernetesDiscoveryBlockingEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=true");
		Assertions.assertEquals(context.getBeanNamesForType(SpringCloudKubernetesInformerFactoryProcessor.class).length,
				1);
		Assertions.assertEquals(context.getBeanNamesForType(KubernetesInformerDiscoveryClient.class).length, 1);
	}

	@Test
	void kubernetesDiscoveryBlockingDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.discovery.blocking.enabled=false");
		Assertions.assertEquals(context.getBeanNamesForType(SpringCloudKubernetesInformerFactoryProcessor.class).length,
				0);
		Assertions.assertEquals(context.getBeanNamesForType(KubernetesInformerDiscoveryClient.class).length, 0);
	}

	private void setup(String... properties) {
		context = new SpringApplicationBuilder(KubernetesInformerDiscoveryAutoConfiguration.class,
				KubernetesClientAutoConfiguration.class).web(WebApplicationType.NONE).properties(properties).run();
	}

}
