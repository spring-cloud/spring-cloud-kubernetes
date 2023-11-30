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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource;

/**
 * test that asserts the type of published event for blocking discovery.
 *
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.cloud-platform=kubernetes", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.discovery-server-url=http://example" },
		classes = BlockingDiscoveryHealthPublishedEventTest.HealthEventListenerConfiguration.class)
class BlockingDiscoveryHealthPublishedEventTest {

	private static boolean caught;

	@Test
	void test() {
		Assertions.assertTrue(caught);
	}

	@TestConfiguration
	static class HealthEventListenerConfiguration {

		@Bean
		HealthEventListener healthEventListener() {
			return new HealthEventListener();
		}

	}

	private static class HealthEventListener implements ApplicationListener<InstanceRegisteredEvent<?>> {

		@Override
		public void onApplicationEvent(InstanceRegisteredEvent<?> event) {
			caught = true;
			Assertions.assertTrue(event.getSource() instanceof RegisteredEventSource);
			RegisteredEventSource registeredEventSource = (RegisteredEventSource) event.getSource();
			Assertions.assertTrue(registeredEventSource.inside());
			Assertions.assertNull(registeredEventSource.pod());
			Assertions.assertEquals(registeredEventSource.cloudPlatform(), "kubernetes");
		}

	}

}
