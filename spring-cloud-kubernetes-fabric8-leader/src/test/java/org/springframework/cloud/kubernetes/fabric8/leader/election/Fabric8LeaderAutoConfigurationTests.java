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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.fabric8.leader.Fabric8LeaderAutoConfiguration;

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
	 *     - Fabric8LeaderAutoConfiguration         must be picked up
	 *     - Fabric8LeaderElectionAutoConfiguration must not be picked up
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationMissing() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LeaderApp.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LeaderAutoConfiguration.class,
					Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class))
			.run(context -> {
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderAutoConfiguration.class);
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = false
	 *
	 *     As such:
	 *
	 *     - Fabric8LeaderAutoConfiguration         must be picked up
	 *     - Fabric8LeaderElectionAutoConfiguration must not be picked up
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationPresentEqualToFalse() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LeaderApp.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LeaderAutoConfiguration.class,
					Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class))
			.withPropertyValues("spring.cloud.kubernetes.leader.election.enabled=false")
			.run(context -> {
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderAutoConfiguration.class);
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = true
	 *
	 *     As such:
	 *
	 *     - Fabric8LeaderAutoConfiguration         must not be picked up
	 *     - Fabric8LeaderElectionAutoConfiguration must be picked up
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationPresentEqualToTrue() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LeaderApp.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LeaderAutoConfiguration.class,
					Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class))
			.withPropertyValues("spring.cloud.kubernetes.leader.election.enabled=true",
					"spring.main.cloud-platform=kubernetes")
			.run(context -> {
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderAutoConfiguration.class);
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = true
	 *     - management.info.leader.election.enabled = true
	 *
	 *     As such:
	 *
	 *     - Fabric8LeaderAutoConfiguration         must not be picked up
	 *     - Fabric8LeaderElectionAutoConfiguration must be picked up
	 *     - Fabric8LeaderElectionInfoContributor   must be picked up
	 * </pre>
	 */
	@Test
	void leaderInfoContributorPresent() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LeaderApp.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LeaderAutoConfiguration.class,
					Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=kubernetes", "management.info.leader.election.enabled=true",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> {
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderAutoConfiguration.class);
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionInfoContributor.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = true
	 *     - management.info.leader.election.enabled = false
	 *
	 *     As such:
	 *
	 *     - Fabric8LeaderAutoConfiguration         must not be picked up
	 *     - Fabric8LeaderElectionAutoConfiguration must be picked up
	 *     - Fabric8LeaderElectionInfoContributor   must not be picked up
	 * </pre>
	 */
	@Test
	void leaderInfoContributorMissing() {
		new ApplicationContextRunner().withUserConfiguration(Fabric8LeaderApp.class)
			.withConfiguration(AutoConfigurations.of(Fabric8LeaderAutoConfiguration.class,
					Fabric8LeaderElectionAutoConfiguration.class,
					Fabric8LeaderElectionCallbacksAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=kubernetes",
					"management.info.leader.election.enabled=false",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> {
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderAutoConfiguration.class);
				Assertions.assertThat(context).hasSingleBean(Fabric8LeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context).doesNotHaveBean(Fabric8LeaderElectionInfoContributor.class);
			});
	}

}
