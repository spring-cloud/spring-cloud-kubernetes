/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.leader.election;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * @author wind57
 */
class LeaderElectionPropertiesTests {

	@Test
	void testDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
			LeaderElectionProperties properties = context.getBean(LeaderElectionProperties.class);
			Assertions.assertNotNull(properties);
			Assertions.assertTrue(properties.publishEvents());
			Assertions.assertTrue(properties.waitForPodReady());
			Assertions.assertEquals(15, properties.leaseDuration());
			Assertions.assertEquals("default", properties.lockNamespace());
			Assertions.assertEquals("spring-k8s-leader-election-lock", properties.lockName());
			Assertions.assertEquals(10, properties.renewDeadline());
			Assertions.assertEquals(2, properties.retryPeriod());
		});
	}

	@Test
	void testNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.cloud.kubernetes.leader.election.wait-for-pod-ready=false",
						"spring.cloud.kubernetes.leader.election.publish-events=false",
						"spring.cloud.kubernetes.leader.election.lease-duration=10",
						"spring.cloud.kubernetes.leader.election.lock-namespace=lock-namespace",
						"spring.cloud.kubernetes.leader.election.lock-name=lock-name",
						"spring.cloud.kubernetes.leader.election.renew-deadline=1",
						"spring.cloud.kubernetes.leader.election.retry-period=3")
				.run(context -> {
					LeaderElectionProperties properties = context.getBean(LeaderElectionProperties.class);
					Assertions.assertNotNull(properties);
					Assertions.assertFalse(properties.waitForPodReady());
					Assertions.assertFalse(properties.publishEvents());
					Assertions.assertEquals(10, properties.leaseDuration());
					Assertions.assertEquals("lock-namespace", properties.lockNamespace());
					Assertions.assertEquals("lock-name", properties.lockName());
					Assertions.assertEquals(1, properties.renewDeadline());
					Assertions.assertEquals(3, properties.retryPeriod());
				});
	}

	@EnableConfigurationProperties(LeaderElectionProperties.class)
	@Configuration
	static class Config {

	}

}
