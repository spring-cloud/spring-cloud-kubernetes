/*
 * Copyright 2013-2022 the original author or authors.
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

import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class SourceDataEntriesProcessorTests {

	@Test
	void testSingleYml() {

		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(Map.of("one.yml", "key: \n value"),
				new MockEnvironment());

		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("value", result.get("key"));
	}

	@Test
	void testSingleYaml() {

		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(Map.of("one.yaml", "key: \n value"),
				new MockEnvironment());

		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("value", result.get("key"));
	}

	@Test
	void testSingleProperties() {

		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(Map.of("one.properties", "key=value"),
				new MockEnvironment());

		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("value", result.get("key"));
	}

	/**
	 * <pre>
	 *	two properties present, none are file treated
	 * </pre>
	 */
	@Test
	void twoEntriesNoneFileTreated() {

		Map.Entry<String, String> one = Map.entry("one", "1");
		Map.Entry<String, String> two = Map.entry("two", "2");
		Map<String, String> map = Map.ofEntries(one, two);

		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, new MockEnvironment());

		Assertions.assertEquals(2, result.size());
		Assertions.assertEquals("1", result.get("one"));
		Assertions.assertEquals("2", result.get("two"));
	}

	/**
	 * <pre>
	 *	- two properties present, none are file treated.
	 *  - even if there is a application.yaml, it is not taken since it is != spring.application.name
	 * </pre>
	 */
	@Test
	void twoEntriesOneIsYamlButNotTaken() {

		Map.Entry<String, String> one = Map.entry("one", "1");
		Map.Entry<String, String> myName = Map.entry("my-name.yaml", "color: \n blue");
		Map<String, String> map = Map.ofEntries(one, myName);

		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, new MockEnvironment());

		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("1", result.get("one"));
	}

	/**
	 * <pre>
	 *	- two properties present, both taken.
	 *  - second one is treated as a file, since it's name matches "spring.application.name"
	 * </pre>
	 */
	@Test
	void twoEntriesBothTaken() {

		Map.Entry<String, String> one = Map.entry("one", "1");
		Map.Entry<String, String> application = Map.entry("application.yaml", "color: \n blue");
		Map<String, String> map = Map.ofEntries(one, application);

		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, new MockEnvironment());

		Assertions.assertEquals(2, result.size());
		Assertions.assertEquals("1", result.get("one"));
		Assertions.assertEquals("blue", result.get("color"));
	}

	/**
	 * <pre>
	 *	- three properties present, all taken.
	 *  - second one is treated as a file, since it's name matches "spring.application.name"
	 *  - third one is taken since it matches one active profile
	 * </pre>
	 */
	@Test
	void threeEntriesAllTaken() {

		Map.Entry<String, String> one = Map.entry("one", "1");
		Map.Entry<String, String> application = Map.entry("application.properties", "color=blue");
		Map.Entry<String, String> applicationDev = Map.entry("application-dev.properties", "fit=sport");
		Map<String, String> map = Map.ofEntries(one, application, applicationDev);

		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("dev");
		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, env);

		Assertions.assertEquals(3, result.size());
		Assertions.assertEquals("1", result.get("one"));
		Assertions.assertEquals("blue", result.get("color"));
		Assertions.assertEquals("sport", result.get("fit"));
	}

	/**
	 * <pre>
	 *	- five properties present, four are taken
	 *  - second one is treated as a file, since it's name matches "spring.application.name"
	 *  - third one is taken since it matches one active profile
	 *  - fourth one is taken since it matches one active profile
	 * </pre>
	 */
	@Test
	void fiveEntriesFourTaken() {

		Map.Entry<String, String> one = Map.entry("one", "1");
		Map.Entry<String, String> jacket = Map.entry("jacket.properties", "name=jacket");
		Map.Entry<String, String> jacketFit = Map.entry("jacket-fit.properties", "fit=sport");
		Map.Entry<String, String> jacketColor = Map.entry("jacket-color.properties", "color=black");
		Map.Entry<String, String> jacketSeason = Map.entry("jacket-season.properties", "season=summer");
		Map<String, String> map = Map.ofEntries(one, jacket, jacketFit, jacketColor, jacketSeason);

		MockEnvironment env = new MockEnvironment();
		env.setProperty("spring.application.name", "jacket");
		env.setActiveProfiles("fit", "color");
		Map<String, Object> result = SourceDataEntriesProcessor.processAllEntries(map, env);

		Assertions.assertEquals(4, result.size());
		Assertions.assertEquals("1", result.get("one"));
		Assertions.assertEquals("jacket", result.get("name"));
		Assertions.assertEquals("sport", result.get("fit"));
		Assertions.assertEquals("black", result.get("color"));
	}

}
