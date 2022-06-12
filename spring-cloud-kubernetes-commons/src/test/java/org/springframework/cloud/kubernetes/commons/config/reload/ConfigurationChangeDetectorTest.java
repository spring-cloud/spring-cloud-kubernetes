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

package org.springframework.cloud.kubernetes.commons.config.reload;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class ConfigurationChangeDetectorTest {

	private final ConfigurationChangeDetectorStub stub = new ConfigurationChangeDetectorStub(new MockEnvironment(),
			new ConfigReloadProperties(), new ConfigurationUpdateStrategy("some", () -> {

			}));

	@Test
	void testChangedTwoNulls() {
		boolean changed = stub.changed(null, (MapPropertySource) null);
		assertThat(changed).isFalse();
	}

	@Test
	void testChangedLeftNullRightNonNull() {
		MapPropertySource right = new MapPropertySource("rightNonNull", Collections.emptyMap());
		boolean changed = stub.changed(null, right);
		assertThat(changed).isTrue();
	}

	@Test
	void testChangedLeftNonNullRightNull() {
		MapPropertySource left = new MapPropertySource("leftNonNull", Collections.emptyMap());
		boolean changed = stub.changed(left, null);
		assertThat(changed).isTrue();
	}

	@Test
	void testChangedEqualMaps() {
		Object value = new Object();
		Map<String, Object> leftMap = new HashMap<>();
		leftMap.put("key", value);
		Map<String, Object> rightMap = new HashMap<>();
		rightMap.put("key", value);
		MapPropertySource left = new MapPropertySource("left", leftMap);
		MapPropertySource right = new MapPropertySource("right", rightMap);
		boolean changed = stub.changed(left, right);
		assertThat(changed).isFalse();
	}

	@Test
	void testChangedNonEqualMaps() {
		Object value = new Object();
		Map<String, Object> leftMap = new HashMap<>();
		leftMap.put("key", value);
		leftMap.put("anotherKey", value);
		Map<String, Object> rightMap = new HashMap<>();
		rightMap.put("key", value);
		MapPropertySource left = new MapPropertySource("left", leftMap);
		MapPropertySource right = new MapPropertySource("right", rightMap);
		boolean changed = stub.changed(left, right);
		assertThat(changed).isTrue();
	}

	@Test
	void testChangedListsDifferentSizes() {
		List<MapPropertySource> left = Collections.singletonList(new MapPropertySource("one", Collections.emptyMap()));
		List<MapPropertySource> right = Collections.emptyList();
		boolean changed = stub.changed(left, right);
		assertThat(changed).isFalse();
	}

	@Test
	void testChangedListSameSizesButNotEqual() {
		Object value = new Object();
		Map<String, Object> leftMap = new HashMap<>();
		leftMap.put("key", value);
		Map<String, Object> rightMap = new HashMap<>();
		leftMap.put("anotherKey", value);
		List<MapPropertySource> left = Collections.singletonList(new MapPropertySource("one", leftMap));
		List<MapPropertySource> right = Collections.singletonList(new MapPropertySource("two", rightMap));
		boolean changed = stub.changed(left, right);
		assertThat(changed).isTrue();
	}

	@Test
	void testChangedListSameSizesEqual() {
		Object value = new Object();
		Map<String, Object> leftMap = new HashMap<>();
		leftMap.put("key", value);
		Map<String, Object> rightMap = new HashMap<>();
		leftMap.put("key", value);
		List<MapPropertySource> left = Collections.singletonList(new MapPropertySource("one", leftMap));
		List<MapPropertySource> right = Collections.singletonList(new MapPropertySource("two", rightMap));
		boolean changed = stub.changed(left, right);
		assertThat(changed).isTrue();
	}

	/**
	 * only needed to test some protected methods it defines
	 */
	private static final class ConfigurationChangeDetectorStub extends ConfigurationChangeDetector {

		private ConfigurationChangeDetectorStub(ConfigurableEnvironment environment, ConfigReloadProperties properties,
				ConfigurationUpdateStrategy strategy) {
			super(environment, properties, strategy);
		}

	}

}
