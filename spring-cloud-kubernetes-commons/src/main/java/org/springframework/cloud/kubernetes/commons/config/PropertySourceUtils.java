/*
 * Copyright 2013-2019 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;

import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.ABSTAIN;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.FOUND;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.NOT_FOUND;
import static org.springframework.cloud.kubernetes.commons.config.Constants.SPRING_CONFIG_ACTIVATE_ON_PROFILE;
import static org.springframework.cloud.kubernetes.commons.config.Constants.SPRING_PROFILES;

/**
 * Utility class to work with property sources.
 *
 * @author Georgios Andrianakis
 * @author Michael Moudatsos
 */
public final class PropertySourceUtils {

	private PropertySourceUtils() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	/**
	 * Function to convert a String to Properties.
	 */
	public static final Function<String, Properties> KEY_VALUE_TO_PROPERTIES = s -> {
		Properties properties = new Properties();
		try {
			properties.load(new ByteArrayInputStream(s.getBytes()));
			return properties;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	};

	/**
	 * Function to convert Properties to a Map.
	 */
	public static final Function<Properties, Map<String, Object>> PROPERTIES_TO_MAP = p -> p.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));

	/**
	 * Function to convert String into Properties with an environment.
	 * @param environment Environment.
	 * @return properties.
	 */
	public static Function<String, Properties> yamlParserGenerator(Environment environment) {
		return s -> {
			YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
			yamlFactory.setDocumentMatchers(properties -> {
				if (environment != null) {
					String profiles = null;
					String activeOnProfile = properties.getProperty(SPRING_CONFIG_ACTIVATE_ON_PROFILE);
					String springProfiles = properties.getProperty(SPRING_PROFILES);

					if (activeOnProfile != null) {
						profiles = activeOnProfile;
					}
					else if (springProfiles != null) {
						profiles = springProfiles;
					}

					if (StringUtils.hasText(profiles)) {
						return environment.acceptsProfiles(Profiles.of(profiles)) ? FOUND : NOT_FOUND;
					}
				}
				return ABSTAIN;
			});
			yamlFactory.setResources(new ByteArrayResource(s.getBytes()));
			return yamlFactory.getObject();
		};
	}

	/**
	 * returns a {@link BinaryOperator} that unconditionally throws an
	 * {@link IllegalStateException}.
	 * @param <T> type of the argument
	 * @return a {@link BinaryOperator}
	 */
	public static <T> BinaryOperator<T> throwingMerger() {
		return (left, right) -> {
			throw new IllegalStateException("Duplicate key " + left);
		};
	}

}
