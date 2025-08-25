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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
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
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo("simple-property");
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("value");
	}

	@Test
	void testTwoNonFileProperties() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("one", "1");
		k8sSource.put("two", "2");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo("one");
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("1");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("two");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("2");
	}

	@Test
	void testSingleFileProperty() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put(Constants.APPLICATION_PROPERTIES, "key=value");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo(Constants.APPLICATION_PROPERTIES);
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key=value");
	}

	@Test
	void testApplicationAndSimpleProperty() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put(Constants.APPLICATION_PROPERTIES, "key=value");
		k8sSource.put("simple", "other_value");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo(Constants.APPLICATION_PROPERTIES);
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key=value");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("simple");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("other_value");
	}

	@Test
	void testSimplePropertyAndApplication() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put(Constants.APPLICATION_PROPERTIES, "key=value");

		MockEnvironment mockEnvironment = new MockEnvironment();

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo(Constants.APPLICATION_PROPERTIES);
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key=value");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("simple");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("other_value");
	}

	@Test
	void testSimplePropertyAndTwoApplications() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put(Constants.APPLICATION_PROPERTIES, "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setActiveProfiles("dev");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertThat(result.size()).isEqualTo(3);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo(Constants.APPLICATION_PROPERTIES);
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key=value");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("application-dev.properties");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("key-dev=value-dev");

		Assertions.assertThat(result.get(2).getKey()).isEqualTo("simple");
		Assertions.assertThat(result.get(2).getValue()).isEqualTo("other_value");
	}

	@Test
	void testSimplePropertyAndTwoApplicationsDoNotIncludeDefaultProfile() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put(Constants.APPLICATION_PROPERTIES, "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setActiveProfiles("dev");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment, false);
		Assertions.assertThat(result.size()).isEqualTo(1);

		Assertions.assertThat(result.get(0).getKey()).isEqualTo("application-dev.properties");
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key-dev=value-dev");
	}

	@Test
	void testComplex() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("second-simple", "second_other_value");
		k8sSource.put(Constants.APPLICATION_PROPERTIES, "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");
		k8sSource.put("application-k8s.properties", "key-k8s=value-k8s");
		k8sSource.put("ignored.properties", "key-ignored=value-ignored");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setActiveProfiles("k8s");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertThat(result.size()).isEqualTo(4);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo(Constants.APPLICATION_PROPERTIES);
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key=value");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("application-k8s.properties");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("key-k8s=value-k8s");

		Assertions.assertThat(result.get(2).getKey()).isEqualTo("simple");
		Assertions.assertThat(result.get(2).getValue()).isEqualTo("other_value");

		Assertions.assertThat(result.get(3).getKey()).isEqualTo("second-simple");
		Assertions.assertThat(result.get(3).getValue()).isEqualTo("second_other_value");
	}

	@Test
	void testComplexWithNonDefaultApplicationName() {

		Map<String, String> k8sSource = new LinkedHashMap<>();
		k8sSource.put("simple", "other_value");
		k8sSource.put("second-simple", "second_other_value");
		k8sSource.put(Constants.APPLICATION_PROPERTIES, "key=value");
		k8sSource.put("application-dev.properties", "key-dev=value-dev");
		k8sSource.put("application-k8s.properties", "key-k8s=value-k8s");
		k8sSource.put("ignored.properties", "key-ignored=value-ignored");

		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.application.name", "sorted");
		mockEnvironment.setActiveProfiles("k8s");

		List<Map.Entry<String, String>> result = SourceDataEntriesProcessor.sorted(k8sSource, mockEnvironment);
		Assertions.assertThat(result.size()).isEqualTo(2);

		Assertions.assertThat(result.get(0).getKey()).isEqualTo("simple");
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("other_value");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("second-simple");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("second_other_value");
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
		Assertions.assertThat(result.size()).isEqualTo(4);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo("sorted.properties");
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key=value");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("sorted-k8s.properties");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("key-k8s=value-k8s");

		Assertions.assertThat(result.get(2).getKey()).isEqualTo("simple");
		Assertions.assertThat(result.get(2).getValue()).isEqualTo("other_value");

		Assertions.assertThat(result.get(3).getKey()).isEqualTo("second-simple");
		Assertions.assertThat(result.get(3).getValue()).isEqualTo("second_other_value");
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
		Assertions.assertThat(result.size()).isEqualTo(3);
		Assertions.assertThat(result.get(0).getKey()).isEqualTo("sorted-k8s.properties");
		Assertions.assertThat(result.get(0).getValue()).isEqualTo("key-k8s=value-k8s");

		Assertions.assertThat(result.get(1).getKey()).isEqualTo("simple");
		Assertions.assertThat(result.get(1).getValue()).isEqualTo("other_value");

		Assertions.assertThat(result.get(2).getKey()).isEqualTo("second-simple");
		Assertions.assertThat(result.get(2).getValue()).isEqualTo("second_other_value");
	}

}
