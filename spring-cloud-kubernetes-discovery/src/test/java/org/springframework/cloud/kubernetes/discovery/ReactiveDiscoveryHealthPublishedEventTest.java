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

package org.springframework.cloud.kubernetes.discovery;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * test that asserts the type of published event for reactive discovery.
 *
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.cloud-platform=kubernetes", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://example",
				// disable blocking implementation
				"spring.cloud.discovery.blocking.enabled=false" },
		classes = { HealthEventListenerConfiguration.class, App.class })
class ReactiveDiscoveryHealthPublishedEventTest {

	// blocking client is not present, as such the blocking auto-configuration was not
	// picked up, therefor the health event comes from the reactive one.
	@Autowired
	private ObjectProvider<KubernetesDiscoveryClient> discoveryClients;

	@AfterEach
	void afterEach() {
		HealthEventListenerConfiguration.caught = false;
	}

	@Test
	void test() {
		Assertions.assertThat(HealthEventListenerConfiguration.caught).isTrue();
		Assertions.assertThat(discoveryClients.getIfAvailable()).isNull();
	}

}
