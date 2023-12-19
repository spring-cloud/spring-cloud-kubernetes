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

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource;

/**
 * @author wind57
 */
@TestConfiguration
class HealthEventListenerConfiguration {

	static boolean caught = false;

	@Bean
	HealthEventListener healthEventListener() {
		return new HealthEventListener();
	}

	private static class HealthEventListener implements ApplicationListener<InstanceRegisteredEvent<?>> {

		@Override
		public void onApplicationEvent(InstanceRegisteredEvent<?> event) {
			caught = true;
			Assertions.assertInstanceOf(RegisteredEventSource.class, event.getSource());
			RegisteredEventSource registeredEventSource = (RegisteredEventSource) event.getSource();
			Assertions.assertTrue(registeredEventSource.inside());
			Assertions.assertNull(registeredEventSource.pod());
			Assertions.assertEquals(registeredEventSource.cloudPlatform(), "kubernetes");
		}

	}

}
