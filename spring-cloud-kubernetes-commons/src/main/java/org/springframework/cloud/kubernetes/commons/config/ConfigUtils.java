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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.config.Constants.FALLBACK_APPLICATION_NAME;
import static org.springframework.cloud.kubernetes.commons.config.Constants.SPRING_APPLICATION_NAME;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
public final class ConfigUtils {

	private static final Log LOG = LogFactory.getLog(ConfigUtils.class);

	private ConfigUtils() {
	}

	public static String getApplicationName(Environment env, String configName, String configurationTarget) {
		if (!StringUtils.hasLength(configName)) {
			LOG.debug(configurationTarget + " name has not been set, taking it from property/env "
					+ SPRING_APPLICATION_NAME + " (default=" + FALLBACK_APPLICATION_NAME + ")");
			configName = env.getProperty(SPRING_APPLICATION_NAME, FALLBACK_APPLICATION_NAME);
		}

		return configName;
	}

	/**
	 *
	 * @param explicitPrefix value of 'spring.cloud.kubernetes.config.sources.explicitPrefix'
	 * @param useNameAsPrefix value of 'spring.cloud.kubernetes.config.sources.useNameAsPrefix'
	 * @param defaultUseNameAsPrefix value of 'spring.cloud.kubernetes.config.defaultUseNameAsPrefix'
	 * @param normalizedName either the name of 'spring.cloud.kubernetes.config.sources.name' or
	 *      'spring.cloud.kubernetes.config.name'
	 *
	 * @return prefix to use in normalized sources, never null
	 */
	public static String findPrefix(String explicitPrefix, Boolean useNameAsPrefix,
			boolean defaultUseNameAsPrefix, String normalizedName) {
		// if explicitPrefix is set, it takes priority over useNameAsPrefix
		// (either the one from 'spring.cloud.kubernetes.config' or
		// 'spring.cloud.kubernetes.config.sources')
		if (StringUtils.hasText(explicitPrefix)) {
			return explicitPrefix;
		}

		// useNameAsPrefix is a java.lang.Boolean and if it's != null, users have
		// specified it explicitly
		if (useNameAsPrefix != null) {
			if (useNameAsPrefix) {
				return normalizedName;
			}
			return "";
		}

		if (defaultUseNameAsPrefix) {
			return normalizedName;
		}

		return "";
	}

}
