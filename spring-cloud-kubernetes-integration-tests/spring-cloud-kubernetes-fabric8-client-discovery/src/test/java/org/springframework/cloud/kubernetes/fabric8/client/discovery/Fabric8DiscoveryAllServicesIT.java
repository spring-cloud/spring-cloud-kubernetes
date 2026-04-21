/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.Fabric8ClientIntegrationTest;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.TestAssertions.assertAllServices;

/**
 * @author wind57
 */
@Fabric8ClientIntegrationTest(namespaces = "default", busyboxNamespaces = "default", deployExternalNameService = true)
class Fabric8DiscoveryAllServicesIT extends Fabric8DiscoveryBase {

	@Nested
	@TestPropertySource(properties = { "spring.cloud.kubernetes.discovery.include-external-name-services=true" })
	class NonBootstrap {

		@Autowired
		private DiscoveryClient discoveryClient;

		/**
		 * <pre>
		 * 		- there are 3 services : 'busybox-service', 'kubernetes', 'external-name-service'
		 * 		- all of them are found
		 * </pre>
		 */
		@Test
		void test() {
			assertAllServices(discoveryClient);
		}

	}

	@Nested
	@TestPropertySource(properties = { "spring.cloud.kubernetes.discovery.include-external-name-services=true",
			"spring.cloud.bootstrap.enabled=true" })
	class Bootstrap {

		@Autowired
		private DiscoveryClient discoveryClient;

		/**
		 * <pre>
		 * 		- there are 3 services : 'busybox-service', 'kubernetes', 'external-name-service'
		 * 		- all of them are found
		 * </pre>
		 */
		@Test
		void test() {
			assertAllServices(discoveryClient);
		}

	}

}
