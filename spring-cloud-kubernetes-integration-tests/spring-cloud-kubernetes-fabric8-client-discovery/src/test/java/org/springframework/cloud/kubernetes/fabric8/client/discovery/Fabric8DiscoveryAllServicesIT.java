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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import java.io.InputStream;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.TestAssertions.assertAllServices;

/**
 * @author wind57
 */
class Fabric8DiscoveryAllServicesIT extends Fabric8DiscoveryBase {


	private static Service externalServiceName;

	@BeforeAll
	static void beforeAllInNested() {
		InputStream externalNameServiceStream = util.inputStream("external-name-service.yaml");
		externalServiceName = Serialization.unmarshal(externalNameServiceStream, Service.class);
	}

	private void externalNameServices(Phase phase) {
		if (phase == Phase.CREATE) {
			util.createAndWait(NAMESPACE, null, null, externalServiceName, null, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, null, externalServiceName, null);
		}
	}

	@Nested
	@TestPropertySource(properties = { "spring.cloud.kubernetes.discovery.include-external-name-services=true" })
	class NonBootstrap {

		@Autowired
		private DiscoveryClient discoveryClient;

		@BeforeEach
		void beforeEach() {
			Images.loadBusybox(K3S);
			util.busybox(NAMESPACE, Phase.CREATE);
			externalNameServices(Phase.CREATE);
		}

		@AfterEach
		void afterEach() {
			util.busybox(NAMESPACE, Phase.DELETE);
			externalNameServices(Phase.DELETE);
		}

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

		@BeforeEach
		void beforeEach() {
			Images.loadBusybox(K3S);
			util.busybox(NAMESPACE, Phase.CREATE);
			externalNameServices(Phase.CREATE);
		}

		@AfterEach
		void afterEach() {
			util.busybox(NAMESPACE, Phase.DELETE);
			externalNameServices(Phase.DELETE);
		}

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
