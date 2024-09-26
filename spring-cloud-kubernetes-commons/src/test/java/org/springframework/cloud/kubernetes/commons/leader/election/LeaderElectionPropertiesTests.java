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

import java.time.Duration;

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
			Assertions.assertEquals(Duration.ofSeconds(15), properties.leaseDuration());
			Assertions.assertEquals("default", properties.lockNamespace());
			Assertions.assertEquals("spring-k8s-leader-election-lock", properties.lockName());
			Assertions.assertEquals(Duration.ofSeconds(10), properties.renewDeadline());
			Assertions.assertEquals(Duration.ofSeconds(2), properties.retryPeriod());
			Assertions.assertEquals(Duration.ofSeconds(0), properties.waitAfterRenewalFailure());
			Assertions.assertFalse(properties.useConfigMapAsLock());
		});
	}

	@Test
	void testNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
			.withPropertyValues("spring.cloud.kubernetes.leader.election.wait-for-pod-ready=false",
					"spring.cloud.kubernetes.leader.election.publish-events=false",
					"spring.cloud.kubernetes.leader.election.lease-duration=10s",
					"spring.cloud.kubernetes.leader.election.lock-namespace=lock-namespace",
					"spring.cloud.kubernetes.leader.election.lock-name=lock-name",
					"spring.cloud.kubernetes.leader.election.renew-deadline=2d",
					"spring.cloud.kubernetes.leader.election.retry-period=3m",
					"spring.cloud.kubernetes.leader.election.wait-after-renewal-failure=13m",
					"spring.cloud.kubernetes.leader.election.use-config-map-as-lock=true")
			.run(context -> {
				LeaderElectionProperties properties = context.getBean(LeaderElectionProperties.class);
				Assertions.assertNotNull(properties);
				Assertions.assertFalse(properties.waitForPodReady());
				Assertions.assertFalse(properties.publishEvents());
				Assertions.assertEquals(Duration.ofSeconds(10), properties.leaseDuration());
				Assertions.assertEquals("lock-namespace", properties.lockNamespace());
				Assertions.assertEquals("lock-name", properties.lockName());
				Assertions.assertEquals(Duration.ofDays(2), properties.renewDeadline());
				Assertions.assertEquals(Duration.ofMinutes(3), properties.retryPeriod());
				Assertions.assertEquals(Duration.ofMinutes(13), properties.waitAfterRenewalFailure());
				Assertions.assertTrue(properties.useConfigMapAsLock());
			});
	}

	@EnableConfigurationProperties(LeaderElectionProperties.class)
	@Configuration
	static class Config {

	}

}
