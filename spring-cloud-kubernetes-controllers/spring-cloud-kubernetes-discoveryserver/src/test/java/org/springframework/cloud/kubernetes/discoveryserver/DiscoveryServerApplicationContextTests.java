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

package org.springframework.cloud.kubernetes.discoveryserver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;

/**
 * @author wind57
 */
class DiscoveryServerApplicationContextTests {

	@Nested
	@SpringBootTest(classes = TestConfig.class,
			properties = { "spring.cloud.kubernetes.http.discovery.catalog.watcher.enabled=true",
					/* disable kubernetes from liveness and readiness */
					"management.health.livenessstate.enabled=true",
					"management.endpoint.health.group.liveness.include=livenessState",
					"management.health.readinessstate.enabled=true",
					"management.endpoint.health.group.readiness.include=readinessState" })
	class BothControllersPresent {

		@Autowired
		private ObjectProvider<DiscoveryServerController> discoveryServerController;

		@Autowired
		private ObjectProvider<DiscoveryCatalogWatcherController> discoveryCatalogWatcherController;

		@Autowired
		private ObjectProvider<HeartBeatListener> heartBeatListener;

		@Test
		void test() {
			Assertions.assertNotNull(discoveryServerController.getIfAvailable());
			Assertions.assertNotNull(discoveryCatalogWatcherController.getIfAvailable());
			Assertions.assertNotNull(heartBeatListener.getIfAvailable());
		}

	}

	@Nested
	@SpringBootTest(classes = TestConfig.class,
			properties = { "spring.cloud.kubernetes.discovery.catalog-services-watch.enabled=false",
					"spring.cloud.kubernetes.http.discovery.catalog.watcher.enabled=true",
					/* disable kubernetes from liveness and readiness */
					"management.health.livenessstate.enabled=true",
					"management.endpoint.health.group.liveness.include=livenessState",
					"management.health.readinessstate.enabled=true",
					"management.endpoint.health.group.readiness.include=readinessState" })
	class CatalogControllerNotPresentOne {

		@Autowired
		private ObjectProvider<DiscoveryServerController> discoveryServerController;

		@Autowired
		private ObjectProvider<DiscoveryCatalogWatcherController> discoveryCatalogWatcherController;

		@Autowired
		private ObjectProvider<HeartBeatListener> heartBeatListener;

		@Test
		void test() {
			Assertions.assertNotNull(discoveryServerController.getIfAvailable());
			Assertions.assertNull(discoveryCatalogWatcherController.getIfAvailable());
			Assertions.assertNull(heartBeatListener.getIfAvailable());
		}

	}

	@Nested
	@SpringBootTest(classes = TestConfig.class,
			properties = { "spring.cloud.kubernetes.discovery.catalog-services-watch.enabled=true",
					"spring.cloud.kubernetes.http.discovery.catalog.watcher.enabled=false",
					/* disable kubernetes from liveness and readiness */
					"management.health.livenessstate.enabled=true",
					"management.endpoint.health.group.liveness.include=livenessState",
					"management.health.readinessstate.enabled=true",
					"management.endpoint.health.group.readiness.include=readinessState" })
	class CatalogControllerNotPresentTwo {

		@Autowired
		private ObjectProvider<DiscoveryServerController> discoveryServerController;

		@Autowired
		private ObjectProvider<DiscoveryCatalogWatcherController> discoveryCatalogWatcherController;

		@Autowired
		private ObjectProvider<HeartBeatListener> heartBeatListener;

		@Test
		void test() {
			Assertions.assertNotNull(discoveryServerController.getIfAvailable());
			Assertions.assertNull(discoveryCatalogWatcherController.getIfAvailable());
			Assertions.assertNull(heartBeatListener.getIfAvailable());
		}

	}

	@Nested
	@SpringBootTest(classes = TestConfig.class,
			properties = { "spring.cloud.kubernetes.discovery.catalog-services-watch.enabled=false",
					"spring.cloud.kubernetes.http.discovery.catalog.watcher.enabled=false",
					/* disable kubernetes from liveness and readiness */
					"management.health.livenessstate.enabled=true",
					"management.endpoint.health.group.liveness.include=livenessState",
					"management.health.readinessstate.enabled=true",
					"management.endpoint.health.group.readiness.include=readinessState" })
	class CatalogControllerNotPresentThree {

		@Autowired
		private ObjectProvider<DiscoveryServerController> discoveryServerController;

		@Autowired
		private ObjectProvider<DiscoveryCatalogWatcherController> discoveryCatalogWatcherController;

		@Autowired
		private ObjectProvider<HeartBeatListener> heartBeatListener;

		@Test
		void test() {
			Assertions.assertNotNull(discoveryServerController.getIfAvailable());
			Assertions.assertNull(discoveryCatalogWatcherController.getIfAvailable());
			Assertions.assertNull(heartBeatListener.getIfAvailable());
		}

	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		KubernetesInformerReactiveDiscoveryClient discoveryClient() {
			return Mockito.mock(KubernetesInformerReactiveDiscoveryClient.class);
		}

	}

}
