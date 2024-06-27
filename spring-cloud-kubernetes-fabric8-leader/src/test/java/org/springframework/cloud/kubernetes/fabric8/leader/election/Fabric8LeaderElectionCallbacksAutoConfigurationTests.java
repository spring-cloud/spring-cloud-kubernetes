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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class Fabric8LeaderElectionCallbacksAutoConfigurationTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void allBeansPresent() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=true",
				"spring.cloud.kubernetes.leader.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasBean("holderIdentity");
			assertThat(context).hasBean("podNamespace");
			assertThat(context).hasBean("onStartLeadingCallback");
			assertThat(context).hasBean("onStopLeadingCallback");
			assertThat(context).hasBean("onNewLeaderCallback");
			assertThat(context).hasSingleBean(Fabric8LeaderElectionCallbacks.class);
		});
	}

	@Test
	void allBeansMissing() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean("holderIdentity");
			assertThat(context).doesNotHaveBean("podNamespace");
			assertThat(context).doesNotHaveBean("onStartLeadingCallback");
			assertThat(context).doesNotHaveBean("onStopLeadingCallback");
			assertThat(context).doesNotHaveBean("onNewLeaderCallback");
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionCallbacks.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(Fabric8LeaderElectionCallbacksAutoConfiguration.class,
						Fabric8AutoConfiguration.class, KubernetesCommonsAutoConfiguration.class))
				.withPropertyValues(properties);
	}

}
