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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtilsDataProcessor.processSource;

class ConfigUtilsDataProcessorTests {

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = true
	 *		- emptyActiveProfiles               = does not matter
	 *		- profileBasedSourceName            = does not matter
	 * 		- defaultProfilePresent             = does not matter
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * Since 'includeDefaultProfileData=true', all other arguments are irrelevant and
	 * method must return 'true'.
	 */
	@Test
	void testProcessSourceOne() {
		boolean includeDefaultProfileData = true;
		Environment environment = new MockEnvironment();
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of();

		boolean result = processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 *		- profileBasedSourceName            = false
	 * 		- defaultProfilePresent             = true
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * Since 'defaultProfilePresent=true', this method must return 'true'.
	 */
	@Test
	void testProcessSourceTwo() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("default");
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of();

		boolean result = processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 * 		- profileBasedSourceName            = true
	 * 		- defaultProfilePresent             = false
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * Since 'profileBasedSourceName=true', this method must return 'true'.
	 */
	@Test
	void testProcessSourceThree() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("default");
		String sourceName = "account-default";
		Map<String, String> sourceRawData = Map.of();

		boolean result = processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 *		- profileBasedSourceName            = false
	 *		- defaultProfilePresent             = false
	 *		- rawDataContainsProfileBasedSource = false
	 * </pre>
	 *
	 */
	@Test
	void testProcessSourceFour() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of("one", "1");

		boolean result = processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertFalse(result);
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 * 		- profileBasedSourceName            = false
	 * 		- defaultProfilePresent             = false
	 * 		- rawDataContainsProfileBasedSource = true
	 * </pre>
	 *
	 */
	@Test
	void testProcessSourceFive() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of("one", "1", "account-k8s.properties", "one=11");

		boolean result = processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 *		- emptyActiveProfiles               = false
	 *		- profileBasedSourceName            = true
	 * 		- defaultProfilePresent             = does not matter
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 */
	@Test
	void testProcessSourceSix() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		String sourceName = "account-k8s";
		Map<String, String> sourceRawData = Map.of("one", "1", "account-k8s.properties", "one=11");

		boolean result = processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

}
