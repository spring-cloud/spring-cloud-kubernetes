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

public final class Constants {

	/**
	 * Spring application name property.
	 */
	public static final String SPRING_APPLICATION_NAME = "spring.application.name";

	/**
	 * Default application name.
	 */
	public static final String FALLBACK_APPLICATION_NAME = "application";

	/**
	 * Property separator.
	 */
	public static final String PROPERTY_SOURCE_NAME_SEPARATOR = ".";

	/**
	 * Property for legacy profile specific configuration.
	 */
	public static final String SPRING_PROFILES = "spring.profiles";

	/**
	 * Property for profile specific configuration.
	 */
	public static final String SPRING_CONFIG_ACTIVATE_ON_PROFILE = "spring.config.activate.on-profile";

	/**
	 * application.yml property.
	 */
	public static final String APPLICATION_YML = "application.yml";

	/**
	 * application.yaml property.
	 */
	public static final String APPLICATION_YAML = "application.yaml";

	/**
	 * application.properties property.
	 */
	public static final String APPLICATION_PROPERTIES = "application.properties";

	/**
	 * prefix of the configMap.
	 */
	public static final String PREFIX = "configmap";

	private Constants() {
	}

}
