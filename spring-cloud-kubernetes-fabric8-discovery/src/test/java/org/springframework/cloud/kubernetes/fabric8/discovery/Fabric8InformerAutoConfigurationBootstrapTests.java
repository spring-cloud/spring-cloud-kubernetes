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

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8BootstrapConfiguration;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies issue #2270 by combining fabric8 config bootstrap and discovery in the same
 * context.
 */
class Fabric8InformerAutoConfigurationBootstrapTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(KubernetesCommonsAutoConfiguration.class, KubernetesBootstrapConfiguration.class,
						KubernetesDiscoveryPropertiesAutoConfiguration.class, Fabric8AutoConfiguration.class,
						Fabric8BootstrapConfiguration.class, Fabric8InformerAutoConfiguration.class))
		.withPropertyValues("spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true",
				"spring.cloud.kubernetes.discovery.enabled=true", "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.client.namespace=test");

	@Test
	void bootstrapAndDiscoveryShareSingleNamespaceProvider() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(Fabric8ConfigMapPropertySourceLocator.class);
			assertThat(context).hasSingleBean(Fabric8InformerAutoConfiguration.class);
			assertThat(context).hasSingleBean(KubernetesNamespaceProvider.class);
		});
	}

}
