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

package org.springframework.cloud.kubernetes.fabric8.leader;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.leader.LeaderInfoContributor;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that verify LeaderInfoContributor can be disabled via configuration property.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.autoStartup=false",
				"management.info.leader.enabled=false" })
class Fabric8LeaderInfoContributorDisabledTests {

	@Autowired
	private ApplicationContext context;

	/**
	 * Test that the LeaderInfoContributor bean is NOT present when
	 * management.info.leader.enabled=false
	 */
	@Test
	void leaderInfoContributorShouldNotBePresent() {
		assertThatThrownBy(() -> context.getBean(LeaderInfoContributor.class))
			.isInstanceOf(NoSuchBeanDefinitionException.class);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfig {

	}

}
