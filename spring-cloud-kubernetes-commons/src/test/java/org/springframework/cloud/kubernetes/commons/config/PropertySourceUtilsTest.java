/*
 * Copyright 2013-2021 the original author or authors.
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
import java.util.Properties;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;

@ExtendWith(MockitoExtension.class)
public class PropertySourceUtilsTest {

	@Mock
	private Environment environment;

	@Test
	void yamlParserGenerator_noProfile() {
		final Function<String, Properties> function = PropertySourceUtils.yamlParserGenerator(environment);
		final Properties properties = function.apply("spring:\n  application:\n    name: myTestApp\n");
		assertThat(properties.getProperty("spring.application.name")).isEqualTo("myTestApp");
		assertThat(properties.getProperty("spring.profiles")).isNull();
		assertThat(properties.getProperty("spring.config.activate.on-profile")).isNull();
	}

	@Test
	void yamlParserGenerator_springProfiles_matchProfile() {
		willReturn(Boolean.TRUE).given(environment).acceptsProfiles(any(Profiles.class));
		final Function<String, Properties> function = PropertySourceUtils.yamlParserGenerator(environment);
		final Properties properties = function.apply(
				"spring:\n  application:\n    name: myTestApp\n---\nspring:\n  profiles: dummy\n  application:\n    name: myDummyApp");
		assertThat(properties.getProperty("spring.application.name")).isEqualTo("myDummyApp");
		assertThat(properties.getProperty("spring.profiles")).isEqualTo("dummy");
		assertThat(properties.getProperty("spring.config.activate.on-profile")).isNull();
	}

	@Test
	void yamlParserGenerator_springProfiles_mismatchProfile() {
		willReturn(Boolean.FALSE).given(environment).acceptsProfiles(any(Profiles.class));
		final Function<String, Properties> function = PropertySourceUtils.yamlParserGenerator(environment);
		final Properties properties = function.apply(
				"spring:\n  application:\n    name: myTestApp\n---\nspring:\n  profiles: dummy\n  application:\n    name: myDummyApp");
		assertThat(properties.getProperty("spring.application.name")).isEqualTo("myTestApp");
		assertThat(properties.getProperty("spring.profiles")).isNull();
		assertThat(properties.getProperty("spring.config.activate.on-profile")).isNull();
	}

	@Test
	void yamlParserGenerator_springConfigActivateOnProfile_matchProfile() {
		willReturn(Boolean.TRUE).given(environment).acceptsProfiles(any(Profiles.class));
		final Function<String, Properties> function = PropertySourceUtils.yamlParserGenerator(environment);
		final Properties properties = function.apply(
				"spring:\n  application:\n    name: myTestApp\n---\nspring:\n  config:\n    activate:\n      on-profile: dummy\n  application:\n    name: myDummyApp");
		assertThat(properties.getProperty("spring.application.name")).isEqualTo("myDummyApp");
		assertThat(properties.getProperty("spring.profiles")).isNull();
		assertThat(properties.getProperty("spring.config.activate.on-profile")).isEqualTo("dummy");
	}

	@Test
	void yamlParserGenerator_springConfigActivateOnProfile_mismatchProfile() {
		willReturn(Boolean.FALSE).given(environment).acceptsProfiles(any(Profiles.class));
		final Function<String, Properties> function = PropertySourceUtils.yamlParserGenerator(environment);
		final Properties properties = function.apply(
				"spring:\n  application:\n    name: myTestApp\n---\nspring:\n  config:\n    activate:\n      on-profile: dummy\n  application:\n    name: myDummyApp");
		assertThat(properties.getProperty("spring.application.name")).isEqualTo("myTestApp");
		assertThat(properties.getProperty("spring.profiles")).isNull();
		assertThat(properties.getProperty("spring.config.activate.on-profile")).isNull();
	}

	@Test
	void keyValueToProperties_noEntryPresent() {
		Properties properties = PropertySourceUtils.KEY_VALUE_TO_PROPERTIES.apply("");
		assertThat(properties).isNotNull();
	}

	@Test
	void keyValueToProperties_oneEntry() {
		Properties properties = PropertySourceUtils.KEY_VALUE_TO_PROPERTIES.apply("a=b");
		assertThat(properties).isNotNull();
		assertThat(properties.getProperty("a")).isEqualTo("b");
	}

	@Test
	void propertiesToMap_empty() {
		Map<String, Object> result = PropertySourceUtils.PROPERTIES_TO_MAP.apply(new Properties());
		assertThat(result).isNotNull();
		assertThat(result).isEmpty();
	}

	@Test
	void propertiesToMap_oneEntry() {
		Properties properties = PropertySourceUtils.KEY_VALUE_TO_PROPERTIES.apply("a=b");
		Map<String, Object> result = PropertySourceUtils.PROPERTIES_TO_MAP.apply(properties);
		assertThat(result).isNotNull();
		assertThat(result.get("a")).isEqualTo("b");
	}

	@Test
	void propertiesToMap_sameKey() {
		Properties properties = PropertySourceUtils.KEY_VALUE_TO_PROPERTIES.apply("a=b\na=c");
		Map<String, Object> result = PropertySourceUtils.PROPERTIES_TO_MAP.apply(properties);
		assertThat(result).isNotNull();
		assertThat(result.get("a")).isEqualTo("c");

	}

}
