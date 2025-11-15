/*
 * Copyright 2013-present the original author or authors.
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

import org.assertj.core.api.Assertions;
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
			Assertions.assertThat(properties).isNotNull();
			Assertions.assertThat(properties.publishEvents()).isTrue();
			Assertions.assertThat(properties.waitForPodReady()).isTrue();
			Assertions.assertThat(properties.leaseDuration()).isEqualTo(Duration.ofSeconds(15));
			Assertions.assertThat(properties.lockNamespace()).isEqualTo("default");
			Assertions.assertThat(properties.lockName()).isEqualTo("spring-k8s-leader-election-lock");
			Assertions.assertThat(properties.renewDeadline()).isEqualTo(Duration.ofSeconds(10));
			Assertions.assertThat(properties.retryPeriod()).isEqualTo(Duration.ofSeconds(2));
			Assertions.assertThat(properties.waitAfterRenewalFailure()).isEqualTo(Duration.ofSeconds(0));
			Assertions.assertThat(properties.useConfigMapAsLock()).isFalse();
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
				Assertions.assertThat(properties).isNotNull();
				Assertions.assertThat(properties.waitForPodReady()).isFalse();
				Assertions.assertThat(properties.publishEvents()).isFalse();
				Assertions.assertThat(properties.leaseDuration()).isEqualTo(Duration.ofSeconds(10));
				Assertions.assertThat(properties.lockNamespace()).isEqualTo("lock-namespace");
				Assertions.assertThat(properties.lockName()).isEqualTo("lock-name");
				Assertions.assertThat(properties.renewDeadline()).isEqualTo(Duration.ofDays(2));
				Assertions.assertThat(properties.retryPeriod()).isEqualTo(Duration.ofMinutes(3));
				Assertions.assertThat(properties.waitAfterRenewalFailure()).isEqualTo(Duration.ofMinutes(13));
				Assertions.assertThat(properties.useConfigMapAsLock()).isTrue();
			});
	}

	@EnableConfigurationProperties(LeaderElectionProperties.class)
	@Configuration
	static class Config {

	}

}
