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

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesDiscoveryPropertiesTests {

	@Test
	void testBindingWhenNoPropertiesProvided() {
		new ApplicationContextRunner().withUserConfiguration(KubernetesDiscoveryPropertiesMetadataTests.Config.class)
				.run(context -> {
					KubernetesDiscoveryProperties props = context.getBean(KubernetesDiscoveryProperties.class);
					assertThat(props).isNotNull();
					assertThat(props.metadata().labelsPrefix()).isNull();
					assertThat(props.metadata().addPorts()).isTrue();
					assertThat(props.metadata().portsPrefix()).isEqualTo("port.");

					assertThat(props.enabled()).isTrue();
					assertThat(props.allNamespaces()).isFalse();
					assertThat(props.namespaces()).isEmpty();
					assertThat(props.waitCacheReady()).isTrue();
					assertThat(props.cacheLoadingTimeoutSeconds()).isEqualTo(60);
					assertThat(props.includeNotReadyAddresses()).isFalse();
					assertThat(props.filter()).isNull();
					assertThat(props.knownSecurePorts()).isEqualTo(Set.of(443, 8443));
					assertThat(props.serviceLabels()).isEmpty();
					assertThat(props.primaryPortName()).isNull();
					assertThat(props.order()).isZero();
					assertThat(props.useEndpointSlices()).isFalse();
					assertThat(props.includeExternalNameServices()).isFalse();
					assertThat(props.discoveryServerUrl()).isNull();
				});
	}

	@Test
	void testBindingWhenSomePropertiesProvided() {
		new ApplicationContextRunner().withUserConfiguration(KubernetesDiscoveryPropertiesMetadataTests.Config.class)
				.withPropertyValues("spring.cloud.kubernetes.discovery.filter=some-filter",
						"spring.cloud.kubernetes.discovery.knownSecurePorts[0]=222",
						"spring.cloud.kubernetes.discovery.metadata.labelsPrefix=labelsPrefix",
						"spring.cloud.kubernetes.discovery.use-endpoint-slices=true",
						"spring.cloud.kubernetes.discovery.namespaces[0]=ns1",
						"spring.cloud.kubernetes.discovery.namespaces[1]=ns2",
						"spring.cloud.kubernetes.discovery.include-external-name-services=true",
						"spring.cloud.kubernetes.discovery.discovery-server-url=http://example")
				.run(context -> {
					KubernetesDiscoveryProperties props = context.getBean(KubernetesDiscoveryProperties.class);
					assertThat(props).isNotNull();
					assertThat(props.metadata().labelsPrefix()).isEqualTo("labelsPrefix");
					assertThat(props.metadata().addPorts()).isTrue();
					assertThat(props.metadata().portsPrefix()).isEqualTo("port.");

					assertThat(props.enabled()).isTrue();
					assertThat(props.allNamespaces()).isFalse();
					assertThat(props.namespaces()).containsExactlyInAnyOrder("ns1", "ns2");
					assertThat(props.waitCacheReady()).isTrue();
					assertThat(props.cacheLoadingTimeoutSeconds()).isEqualTo(60);
					assertThat(props.includeNotReadyAddresses()).isFalse();
					assertThat(props.filter()).isEqualTo("some-filter");
					assertThat(props.knownSecurePorts()).isEqualTo(Set.of(222));
					assertThat(props.serviceLabels()).isEmpty();
					assertThat(props.primaryPortName()).isNull();
					assertThat(props.order()).isZero();
					assertThat(props.useEndpointSlices()).isTrue();
					assertThat(props.includeExternalNameServices()).isTrue();
					assertThat(props.discoveryServerUrl()).isEqualTo("http://example");
				});
	}

	// when we do not specify metadata, @DefaultValue is going to be picked up
	@Test
	void metadataSetToNotNull() {
		new ApplicationContextRunner().withUserConfiguration(KubernetesDiscoveryPropertiesMetadataTests.Config.class)
				.withPropertyValues("spring.cloud.kubernetes.discovery.filter=some-filter").run(context -> {
					KubernetesDiscoveryProperties props = context.getBean(KubernetesDiscoveryProperties.class);
					assertThat(props).isNotNull();
					assertThat(props.metadata().labelsPrefix()).isNull();
					assertThat(props.metadata().addPorts()).isTrue();
					assertThat(props.metadata().portsPrefix()).isEqualTo("port.");
				});
	}

	@Configuration
	@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
	static class Config {

	}

}
