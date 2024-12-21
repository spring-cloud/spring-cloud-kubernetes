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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.TestAssertions.filterMatchesBothNamespacesViaThePredicate;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.cloud.kubernetes.discovery.namespaces[0]=a-uat",
		"spring.cloud.kubernetes.discovery.namespaces[1]=b-uat",
		"spring.cloud.kubernetes.discovery.filter=#root.metadata.namespace matches '^.*uat$'",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.discovery=DEBUG" })
class Fabric8DiscoveryFilterMatchTwoNamespacesIT extends Fabric8DiscoveryBase {

	private static final String NAMESPACE_A_UAT = "a-uat";

	private static final String NAMESPACE_B_UAT = "b-uat";

	@Autowired
	private DiscoveryClient discoveryClient;

	@BeforeEach
	void beforeEach() {
		Images.loadWiremock(K3S);

		util.createNamespace(NAMESPACE_A_UAT);
		util.createNamespace(NAMESPACE_B_UAT);

		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.CREATE, false);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.CREATE, false);

	}

	@AfterEach
	void afterEach() {

		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.DELETE, false);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.DELETE, false);

		util.deleteNamespace(NAMESPACE_A_UAT);
		util.deleteNamespace(NAMESPACE_B_UAT);
	}

	@Test
	void test() {
		filterMatchesBothNamespacesViaThePredicate(discoveryClient);
	}

}
