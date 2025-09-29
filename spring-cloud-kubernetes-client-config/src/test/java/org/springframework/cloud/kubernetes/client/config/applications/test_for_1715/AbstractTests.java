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

package org.springframework.cloud.kubernetes.client.config.applications.test_for_1715;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.kubernetes.client.config.applications.test_for_1715.properties.APropertySourceByLabel;
import org.springframework.cloud.kubernetes.client.config.applications.test_for_1715.properties.APropertySourceByName;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@ActiveProfiles({ "k8s" })
abstract class AbstractTests {

	@Autowired
	private APropertySourceByName aByName;

	@Autowired
	private APropertySourceByLabel aByLabel;

	/**
	 * this one is simply read by name
	 */
	@Test
	void testAByName() {
		assertThat(aByName.aByName()).isEqualTo("aByName");
	}

	/**
	 * this one is read by name + profile
	 */
	@Test
	void testAByNameAndProfile() {
		assertThat(aByName.aByNameK8s()).isEqualTo("aByNameK8s");
	}

	/**
	 * this one is simply read by name
	 */
	@Test
	void testAByLabel() {
		assertThat(aByLabel.aByLabel()).isEqualTo("aByLabel");
	}

	/**
	 * This one is not read at all. This proves that includeProfileSpecificSources is not
	 * relevant for labels based searches. Notice that we do read from: 'a-by-name-k8s',
	 * but not from 'a-by-label-k8s'.
	 */
	@Test
	void testAByLabelAndProfile() {
		assertThat(aByLabel.aByLabelAndProfile()).isNull();
	}

}
