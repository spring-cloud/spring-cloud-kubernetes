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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class SourceDataEntriesProcessorOrderedPropertiesTests {

	/**
	 * <pre>
	 *     - a single property is present
	 * </pre>
	 */
	@Test
	void testSingleNonFileProperty() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("my-key", "my-value");

		MockEnvironment mockEnvironment = new MockEnvironment();
		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, mockEnvironment);

		Assertions.assertEquals(Map.of("my-key", "my-value"), result);
	}

	/**
	 * <pre>
	 *     - a single property from a properties file
	 * </pre>
	 */
	@Test
	void testSingleFileProperty() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put(Constants.APPLICATION_PROPERTIES, "my-key=from-app");

		MockEnvironment mockEnvironment = new MockEnvironment();
		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, mockEnvironment);

		Assertions.assertEquals(Map.of("my-key", "from-app"), result);
	}

	/**
	 * <pre>
	 *     - application.properties contains:
	 *     	{
	 *     	    firstKey=firstFromProperties
	 *     	    secondKey=secondFromProperties
	 *     	}
	 *
	 *     	- a single property exists : {firstKey = abc}
	 *
	 *     	- This proves that the property overrides the value from "application.properties".
	 * </pre>
	 */
	@Test
	void testThree() {

		Map<String, String> map = new LinkedHashMap<>();
		map.put(Constants.APPLICATION_PROPERTIES, """
				firstKey=firstFromProperties
				secondKey=secondFromProperties""");
		map.put("firstKey", "abc");

		MockEnvironment mockEnvironment = new MockEnvironment();
		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, mockEnvironment);

		Assertions.assertEquals(Map.of("firstKey", "abc", "secondKey", "secondFromProperties"), result);
	}

	/**
	 * <pre>
	 *     - application.properties contains:
	 *     	{
	 *     	    firstKey=firstFromProperties
	 *     	    secondKey=secondFromProperties
	 *     	    thirdKey=thirdFromProperties
	 *     	}
	 *
	 *     	- application-dev.properties contains:
	 *     	  {
	 *     	  	  firstKey=firstFromDevProperties
	 *     	      secondKey=secondFromDevProperties
	 *     	  }
	 *
	 *     	- a single property exists : {firstKey = abc}
	 *
	 *     	- This proves that profile specific properties override non-profile
	 *     	  and plain properties override everything.
	 * </pre>
	 */
	@Test
	void testFour() {

		Map<String, String> map = new LinkedHashMap<>();
		map.put(Constants.APPLICATION_PROPERTIES, """
				firstKey=firstFromProperties
				secondKey=secondFromProperties
				thirdKey=thirdFromProperties""");
		map.put("application-dev.properties", """
				firstKey=firstFromDevProperties
				secondKey=secondFromDevProperties""");
		map.put("firstKey", "abc");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setActiveProfiles("dev");
		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, mockEnvironment);

		Assertions.assertEquals(
				Map.of("firstKey", "abc", "secondKey", "secondFromDevProperties", "thirdKey", "thirdFromProperties"),
				result);
	}

	/**
	 * <pre>
	 *     - application.properties contains:
	 *     	{
	 *     	    firstKey=firstFromProperties
	 *     	    secondKey=secondFromProperties
	 *     	    thirdKey=thirdFromProperties
	 *     	}
	 *
	 *     	- application-dev.properties contains:
	 *     	  {
	 *     	  	  firstKey=firstFromDevProperties
	 *     	      secondKey=secondFromDevProperties
	 *     	  }
	 *
	 *     	- a single property exists : {firstKey = abc}
	 *
	 *     	- This proves that profile specific properties override non-profile
	 *     	  and plain properties override everything.
	 *     	  It also proves that non-active profile properties are ignored.
	 * </pre>
	 */
	@Test
	void testFive() {

		Map<String, String> map = new LinkedHashMap<>();
		map.put(Constants.APPLICATION_PROPERTIES, """
				firstKey=firstFromProperties
				secondKey=secondFromProperties
				thirdKey=thirdFromProperties""");
		map.put("application-dev.properties", """
				firstKey=firstFromDevProperties
				secondKey=secondFromDevProperties""");
		map.put("application-k8s.properties", """
				firstKey=firstFromK8sProperties
				secondKey=secondFromK8sProperties""");
		map.put("firstKey", "abc");
		map.put("fourthKey", "def");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setActiveProfiles("dev");
		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, mockEnvironment);

		Assertions.assertEquals(Map.of("firstKey", "abc", "secondKey", "secondFromDevProperties", "thirdKey",
				"thirdFromProperties", "fourthKey", "def"), result);
	}

}
