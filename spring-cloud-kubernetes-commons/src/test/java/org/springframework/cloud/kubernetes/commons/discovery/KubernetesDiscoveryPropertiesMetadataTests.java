/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties.Metadata;

/**
 * @author wind57
 */
class KubernetesDiscoveryPropertiesMetadataTests {

	@Test
	void testDefaultConstructor() {
		Metadata m = Metadata.DEFAULT;
		assertThat(m.addLabels()).isTrue();
		assertThat(m.labelsPrefix()).isNull();
		assertThat(m.addAnnotations()).isTrue();
		assertThat(m.annotationsPrefix()).isNull();
		assertThat(m.addPorts()).isTrue();
		assertThat(m.portsPrefix()).isEqualTo("port.");
	}

	@Test
	void testSpringBindingFields() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.cloud.kubernetes.discovery.metadata.labelsPrefix=labelsPrefix")
				.run(context -> {
					KubernetesDiscoveryProperties props = context.getBean(KubernetesDiscoveryProperties.class);
					assertThat(props).isNotNull();
					assertThat(props.metadata().labelsPrefix()).isEqualTo("labelsPrefix");
					assertThat(props.metadata().addPorts()).isTrue();
					assertThat(props.metadata().portsPrefix()).isEqualTo("port.");
				});
	}

	@Configuration
	@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
	static class Config {

	}

}
