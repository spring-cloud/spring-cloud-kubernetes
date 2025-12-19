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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import java.util.function.BooleanSupplier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * tests that ensure 'spring.cloud.kubernetes.leader.election' enabled correct
 * auto-configurations, when it is enabled/disabled.
 *
 * @author wind57
 */
class Fabric8LeaderAutoConfigurationTests {

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election is not present
	 *
	 *     As such:
	 *
	 *     - Fabric8LeaderElectionAutoConfiguration          is present
	 *     - Fabric8LeaderElectionCallbacksAutoConfiguration is present
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationMissing() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LeaderConfiguration.class)
			.withAllowBeanDefinitionOverriding(true)
			.withUserConfiguration(Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class, TestConfig.class)
			.withPropertyValues("spring.main.cloud-platform=KUBERNETES", "use.mock.config=true")
			.run(context -> {
				// matchIfMissing = true in the annotation, so both are present
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionCallbacksAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = false
	 *
	 *     As such:
	 *
	 *     - Fabric8LeaderElectionAutoConfiguration          is not present
	 *     - Fabric8LeaderElectionCallbacksAutoConfiguration is not present
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationPresentEqualToFalse() {
		new ApplicationContextRunner()
			.withUserConfiguration(Fabric8LeaderConfiguration.class, Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class, TestConfig.class)
			.withPropertyValues("spring.cloud.kubernetes.leader.election.enabled=false",
					"spring.main.cloud-platform=KUBERNETES")
			.run(context -> {
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderElectionCallbacksAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = true
	 *
	 *     As such:
	 *
	 *     - Fabric8LeaderAutoConfiguration must not be picked up
	 *     - Fabric8LeaderElectionAutoConfiguration must be picked up
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationPresentEqualToTrue() {
		new ApplicationContextRunner()
			.withUserConfiguration(Fabric8LeaderConfiguration.class, Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class, TestConfig.class)
			.withAllowBeanDefinitionOverriding(true)
			.withPropertyValues("spring.cloud.kubernetes.leader.election.enabled=true",
					"spring.main.cloud-platform=KUBERNETES", "use.mock.config=true")
			.run(context -> {
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionCallbacksAutoConfiguration.class);
			});
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		BooleanSupplier podReadySupplier() {
			return () -> false;
		}

	}

}
