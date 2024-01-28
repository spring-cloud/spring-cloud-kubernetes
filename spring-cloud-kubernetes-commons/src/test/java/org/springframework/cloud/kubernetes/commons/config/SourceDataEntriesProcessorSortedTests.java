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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class SourceDataEntriesProcessorSortedTests {

	@Test
	void testSingleNonFileProperty() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple-property", "value");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getKey(), "simple-property");
		Assertions.assertEquals(result.get(0).getValue(), "value");
	}

	@Test
	void testTwoNonFileProperties() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("one", "1");
		k8sSource.put("two", "2");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get(0).getKey(), "one");
		Assertions.assertEquals(result.get(0).getValue(), "1");

		Assertions.assertEquals(result.get(1).getKey(), "two");
		Assertions.assertEquals(result.get(1).getValue(), "2");
	}

	@Test
	void testSingleFileProperty() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("application.properties", "key=value");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getKey(), "application.properties");
		Assertions.assertEquals(result.get(0).getValue(), "key=value");
	}

	@Test
	void testApplicationAndSimpleProperty() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("application.properties", "key=value");
		k8sSource.put("simple", "other_value");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get(0).getKey(), "application.properties");
		Assertions.assertEquals(result.get(0).getValue(), "key=value");

		Assertions.assertEquals(result.get(1).getKey(), "simple");
		Assertions.assertEquals(result.get(1).getValue(), "other_value");
	}

	@Test
	void testSimplePropertyAndApplication() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("application.properties", "key=value");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get(0).getKey(), "application.properties");
		Assertions.assertEquals(result.get(0).getValue(), "key=value");

		Assertions.assertEquals(result.get(1).getKey(), "simple");
		Assertions.assertEquals(result.get(1).getValue(), "other_value");
	}

	@Test
	void testSimplePropertyAndTwoApplications() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("application.properties", "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setActiveProfiles("dev");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 3);
		Assertions.assertEquals(result.get(0).getKey(), "application.properties");
		Assertions.assertEquals(result.get(0).getValue(), "key=value");

		Assertions.assertEquals(result.get(1).getKey(), "application-dev.properties");
		Assertions.assertEquals(result.get(1).getValue(), "key-dev=value-dev");

		Assertions.assertEquals(result.get(2).getKey(), "simple");
		Assertions.assertEquals(result.get(2).getValue(), "other_value");
	}

	@Test
	void testComplex() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("second-simple", "second_other_value");
		k8sSource.put("application.properties", "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");
		k8sSource.put("application-k8s.properties", "key-k8s=value-k8s");
		k8sSource.put("ignored.properties", "key-ignored=value-ignored");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setActiveProfiles("k8s");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result.get(0).getKey(), "application.properties");
		Assertions.assertEquals(result.get(0).getValue(), "key=value");

		Assertions.assertEquals(result.get(1).getKey(), "application-k8s.properties");
		Assertions.assertEquals(result.get(1).getValue(), "key-k8s=value-k8s");

		Assertions.assertEquals(result.get(2).getKey(), "simple");
		Assertions.assertEquals(result.get(2).getValue(), "other_value");

		Assertions.assertEquals(result.get(3).getKey(), "second-simple");
		Assertions.assertEquals(result.get(3).getValue(), "second_other_value");
	}

	@Test
	void testComplexWithNonDefaultApplicationName() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("second-simple", "second_other_value");
		k8sSource.put("application.properties", "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");
		k8sSource.put("application-k8s.properties", "key-k8s=value-k8s");
		k8sSource.put("ignored.properties", "key-ignored=value-ignored");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.application.name", "sorted");
		mockEnvironment.setActiveProfiles("k8s");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 2);

		Assertions.assertEquals(result.get(0).getKey(), "simple");
		Assertions.assertEquals(result.get(0).getValue(), "other_value");

		Assertions.assertEquals(result.get(1).getKey(), "second-simple");
		Assertions.assertEquals(result.get(1).getValue(), "second_other_value");
	}

	@Test
	void testComplexWithNonDefaultApplicationNameMoreMatches() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("second-simple", "second_other_value");
		k8sSource.put("sorted.properties", "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");
		k8sSource.put("sorted-k8s.properties", "key-k8s=value-k8s");
		k8sSource.put("ignored.properties", "key-ignored=value-ignored");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.application.name", "sorted");
		mockEnvironment.setActiveProfiles("k8s");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result.get(0).getKey(), "sorted.properties");
		Assertions.assertEquals(result.get(0).getValue(), "key=value");

		Assertions.assertEquals(result.get(1).getKey(), "sorted-k8s.properties");
		Assertions.assertEquals(result.get(1).getValue(), "key-k8s=value-k8s");

		Assertions.assertEquals(result.get(2).getKey(), "simple");
		Assertions.assertEquals(result.get(2).getValue(), "other_value");

		Assertions.assertEquals(result.get(3).getKey(), "second-simple");
		Assertions.assertEquals(result.get(3).getValue(), "second_other_value");
	}

	@Test
	void testProfileBasedOnly() {
		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("second-simple", "second_other_value");
		k8sSource.put("sorted-k8s.properties", "key-k8s=value-k8s");
		k8sSource.put("ignored.properties", "key-ignored=value-ignored");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.application.name", "sorted");
		mockEnvironment.setActiveProfiles("k8s");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertEquals(result.size(), 3);
		Assertions.assertEquals(result.get(0).getKey(), "sorted-k8s.properties");
		Assertions.assertEquals(result.get(0).getValue(), "key-k8s=value-k8s");

		Assertions.assertEquals(result.get(1).getKey(), "simple");
		Assertions.assertEquals(result.get(1).getValue(), "other_value");

		Assertions.assertEquals(result.get(2).getKey(), "second-simple");
		Assertions.assertEquals(result.get(2).getValue(), "second_other_value");
	}

}
