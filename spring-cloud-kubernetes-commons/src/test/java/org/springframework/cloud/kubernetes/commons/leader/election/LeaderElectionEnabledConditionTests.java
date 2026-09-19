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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/**
 * @author wind57
 */
class LeaderElectionEnabledConditionTests {

	@Test
	void leaderElectionEnabledTest() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.withPropertyValues("spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> Assertions.assertThat(context).hasBean("leaderEnabled"));
	}

	@Test
	void haEnabledTest() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true")
			.run(context -> Assertions.assertThat(context).hasBean("leaderEnabled"));
	}

	@Test
	void leaderElectionAndHaEnabledTest() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> Assertions.assertThat(context).hasBean("leaderEnabled"));
	}

	@Test
	void leaderElectionEnabledHaDisabledTest() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=false",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> Assertions.assertThat(context).hasBean("leaderEnabled"));
	}

	@Test
	void leaderElectionDisabledHaEnabledTest() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true",
					"spring.cloud.kubernetes.leader.election.enabled=false")
			.run(context -> Assertions.assertThat(context).hasBean("leaderEnabled"));
	}

	@Test
	void leaderElectionDisabledHaDisabledTest() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=false",
					"spring.cloud.kubernetes.leader.election.enabled=false")
			.run(context -> Assertions.assertThat(context).doesNotHaveBean("leaderEnabled"));
	}

	@Test
	void leaderElectionNotPresentHaNotPresentTest() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.run(context -> Assertions.assertThat(context).doesNotHaveBean("leaderEnabled"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		@ConditionalOnLeaderElectionEnabled
		String leaderEnabled() {
			return "leaderEnabled";
		}

	}

}
