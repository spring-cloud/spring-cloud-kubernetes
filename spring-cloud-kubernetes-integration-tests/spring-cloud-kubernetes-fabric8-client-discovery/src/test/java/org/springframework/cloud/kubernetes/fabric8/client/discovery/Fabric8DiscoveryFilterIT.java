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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.K3sIntegrationTest;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.TestAssertions.filterMatchesBothNamespacesViaThePredicate;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.cloud.kubernetes.discovery.namespaces[0]=a-uat",
		"spring.cloud.kubernetes.discovery.namespaces[1]=b-uat",
		"spring.cloud.kubernetes.discovery.filter=#root.metadata.namespace matches '^.*uat$'",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.discovery=DEBUG" })
@K3sIntegrationTest(namespaces = { "a-uat", "b-uat" }, wiremockNamespaces = { "a-uat", "b-uat" })
class Fabric8DiscoveryFilterIT extends Fabric8DiscoveryBase {

	@Autowired
	private DiscoveryClient discoveryClient;

	@Test
	void test() {
		filterMatchesBothNamespacesViaThePredicate(discoveryClient);
	}

}
