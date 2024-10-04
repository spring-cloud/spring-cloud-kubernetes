/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.catalog.watch.it;

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.suite.api.BeforeSuite;
import org.junit.platform.suite.api.Suite;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.catalog.watch.Application;
import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesCatalogWatchAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;

import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.it.Fabric8CatalogWatchIT.TestConfig;

/**
 * @author wind57
 */
@Suite
@SpringBootTest(
	classes = {
		KubernetesCatalogWatchAutoConfiguration.class,
		TestConfig.class,
		Application.class
	},
	properties = {
		"spring.main.cloud-platform=kubernetes",
		"spring.cloud.config.import-check.enabled=false",
		"spring.cloud.kubernetes.discovery.catalogServicesWatchDelay=2000",
		"spring.cloud.kubernetes.client.namespace=default",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.discovery=DEBUG"
	},
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ExtendWith(OutputCaptureExtension.class)
class Fabric8CatalogWatchIT {

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@LocalServerPort
	private int port;

	@BeforeSuite
	static void beforeAll() {
		K3S.start();
		Images.loadBusybox(K3S);
		util = new Util(K3S);
		util.busybox(NAMESPACE, Phase.CREATE);
	}

	@Test
	void test(CapturedOutput output) {
		TestUtil.assertLogStatement(output, "stateGenerator is of type: Fabric8EndpointsCatalogWatch");
		TestUtil.test(util, NAMESPACE, port);
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		KubernetesClient kubernetesClient() {
			String kubeConfigYaml = K3S.getKubeConfigYaml();
			Config config = Config.fromKubeconfig(kubeConfigYaml);
			return new KubernetesClientBuilder().withConfig(config).build();
		}

		@Bean
		@Primary
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
			return new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(443, 8443), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);
		}

	}

}

