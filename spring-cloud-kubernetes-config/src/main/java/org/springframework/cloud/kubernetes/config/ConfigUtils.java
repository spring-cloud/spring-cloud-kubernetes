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

package org.springframework.cloud.kubernetes.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.config.Constants.FALLBACK_APPLICATION_NAME;
import static org.springframework.cloud.kubernetes.config.Constants.SPRING_APPLICATION_NAME;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
public final class ConfigUtils {

	private static final Log LOG = LogFactory.getLog(ConfigUtils.class);

	private ConfigUtils() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static String getApplicationName(Environment env, String configName, String configurationTarget) {
		if (StringUtils.isEmpty(configName)) {
			// TODO: use relaxed binding
			LOG.debug(configurationTarget + " name has not been set, taking it from property/env "
					+ SPRING_APPLICATION_NAME + " (default=" + FALLBACK_APPLICATION_NAME + ")");
			configName = env.getProperty(SPRING_APPLICATION_NAME, FALLBACK_APPLICATION_NAME);
		}

		return configName;
	}

	public static String getApplicationNamespace(KubernetesClient client, String configNamespace,
			String configurationTarget) {
		if (StringUtils.isEmpty(configNamespace)) {
			LOG.debug(configurationTarget + " namespace has not been set, taking it from client (ns="
					+ client.getNamespace() + ")");
			configNamespace = client.getNamespace();
		}

		return configNamespace;
	}

}
