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

package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.StringUtils;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
public final class Fabric8ConfigUtils {

	private static final Log LOG = LogFactory.getLog(Fabric8ConfigUtils.class);

	private Fabric8ConfigUtils() {
	}

	public static String getApplicationNamespace(KubernetesClient client, String configNamespace,
			String configurationTarget) {
		if (!StringUtils.hasLength(configNamespace)) {
			LOG.debug(configurationTarget + " namespace has not been set, taking it from client (ns="
					+ client.getNamespace() + ")");
			configNamespace = client.getNamespace();
		}

		return configNamespace;
	}

	static String getNamespace(KubernetesClient client, String namespace) {
		return !StringUtils.hasLength(namespace) ? client.getNamespace() : namespace;
	}

}
