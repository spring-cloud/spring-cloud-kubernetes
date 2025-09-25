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

package org.springframework.cloud.kubernetes.commons.config.reload;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.MountConfigMapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class ConfigReloadUtilTests {

	@Test
	void testChangedTwoNulls() {
		boolean changed = ConfigReloadUtil.changed(null, (MapPropertySource) null);
		assertThat(changed).isFalse();
	}

	@Test
	void testChangedLeftNullRightNonNull() {
		MapPropertySource right = new MapPropertySource("rightNonNull", Collections.emptyMap());
		boolean changed = ConfigReloadUtil.changed(null, right);
		assertThat(changed).isTrue();
	}

	@Test
	void testChangedLeftNonNullRightNull() {
		MapPropertySource left = new MapPropertySource("leftNonNull", Collections.emptyMap());
		boolean changed = ConfigReloadUtil.changed(left, null);
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
		boolean changed = ConfigReloadUtil.changed(left, right);
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
		boolean changed = ConfigReloadUtil.changed(left, right);
		assertThat(changed).isTrue();
	}

	@Test
	void testChangedListsDifferentSizes() {
		List<MapPropertySource> left = Collections.singletonList(new MapPropertySource("one", Collections.emptyMap()));
		List<MapPropertySource> right = Collections.emptyList();
		boolean changed = ConfigReloadUtil.changed(left, right);
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
		boolean changed = ConfigReloadUtil.changed(left, right);
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
		boolean changed = ConfigReloadUtil.changed(left, right);
		assertThat(changed).isTrue();
	}

	@Test
	void testFindPropertySources() {
		MockEnvironment environment = new MockEnvironment();
		MutablePropertySources propertySources = environment.getPropertySources();
		propertySources.addFirst(new OneComposite());
		propertySources.addFirst(new PlainPropertySource<>("plain"));
		propertySources.addFirst(new OneBootstrap<>(new EnumerablePropertySource<>("enumerable") {
			@Override
			public String[] getPropertyNames() {
				return new String[0];
			}

			@Override
			public Object getProperty(String name) {
				return null;
			}
		}));
		propertySources.addFirst(new MountConfigMapPropertySource("mounted", Map.of("a", "b")));

		List<? extends PropertySource> result = ConfigReloadUtil.findPropertySources(PlainPropertySource.class,
				environment);

		Assertions.assertThat(result.size()).isEqualTo(3);
		Assertions.assertThat(result.get(0).getProperty("a")).isEqualTo("b");
		Assertions.assertThat(result.get(1).getProperty("")).isEqualTo("plain");
		Assertions.assertThat(result.get(2).getProperty("")).isEqualTo("from-inner-two-composite");

	}

	@Test
	void testSecretsPropertySource() {
		MockEnvironment environment = new MockEnvironment();
		MutablePropertySources propertySources = environment.getPropertySources();
		propertySources.addFirst(new SecretsPropertySource(new SourceData("secret", Map.of("a", "b"))));

		List<? extends PropertySource> result = ConfigReloadUtil.findPropertySources(PlainPropertySource.class,
				environment);
		assertThat(result.size()).isEqualTo(0);
	}

	@Test
	void testBootstrapSecretsPropertySource() {
		MockEnvironment environment = new MockEnvironment();
		MutablePropertySources propertySources = environment.getPropertySources();
		propertySources
			.addFirst(new OneBootstrap<>(new SecretsPropertySource(new SourceData("secret", Map.of("a", "b")))));

		List<? extends PropertySource> result = ConfigReloadUtil.findPropertySources(PlainPropertySource.class,
				environment);
		assertThat(result.size()).isEqualTo(0);
	}

	private static final class OneComposite extends CompositePropertySource {

		private OneComposite() {
			super("one");
		}

		@Override
		public Collection<PropertySource<?>> getPropertySources() {
			return List.of(new TwoComposite());
		}

	}

	private static final class TwoComposite extends CompositePropertySource {

		private TwoComposite() {
			super("two");
		}

		@Override
		public Collection<PropertySource<?>> getPropertySources() {
			return List.of(new PlainPropertySource<>("from-inner-two-composite"));
		}

	}

	private static final class PlainPropertySource<T> extends PropertySource<T> {

		private PlainPropertySource(String name) {
			super(name);
		}

		@Override
		public Object getProperty(String name) {
			return this.name;
		}

	}

	private static final class OneBootstrap<T> extends BootstrapPropertySource<T> {

		private final EnumerablePropertySource<T> delegate;

		private OneBootstrap(EnumerablePropertySource<T> delegate) {
			super(delegate);
			this.delegate = delegate;
		}

		@Override
		public PropertySource<T> getDelegate() {
			return delegate;
		}

	}

}
