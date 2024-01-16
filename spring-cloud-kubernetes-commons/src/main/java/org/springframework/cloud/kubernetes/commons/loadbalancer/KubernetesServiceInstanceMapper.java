/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.loadbalancer;

import java.util.Map;
import java.util.StringJoiner;

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.keysWithPrefix;

/**
 * @author Ryan Baxter
 */
public interface KubernetesServiceInstanceMapper<T> {

	/**
	 * Logger instance.
	 */
	LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesServiceInstanceMapper.class));

	KubernetesServiceInstance map(T service);

	static String createHost(String serviceName, String namespace, String clusterDomain) {
		String namespaceToUse = StringUtils.hasText(namespace) ? namespace : "default";
		return new StringJoiner(".").add(serviceName).add(namespaceToUse).add("svc").add(clusterDomain).toString();
	}

	static boolean isSecure(Map<String, String> labels, Map<String, String> annotations, String servicePortName,
			Integer servicePort) {

		if (hasTrueSecuredValue(labels)) {
			LOG.debug(() -> "Service has a true 'secured' label");
			return true;
		}

		if (hasTrueSecuredValue(annotations)) {
			LOG.debug(() -> "Service has a true 'secured' annotation");
			return true;
		}

		if (servicePortName != null && servicePortName.endsWith("https")) {
			LOG.debug(() -> "Service port name ends with 'https'");
			return true;
		}

		if (servicePort != null && servicePort.toString().endsWith("443")) {
			LOG.debug(() -> "Service port ends with '443'");
			return true;
		}

		return false;
	}

	static Map<String, String> getMapWithPrefixedKeys(Map<String, String> map, String prefix) {
		return keysWithPrefix(map, prefix);
	}

	private static boolean hasTrueSecuredValue(Map<String, String> input) {
		if (input != null) {
			return "true".equals(input.get("secured"));
		}
		return false;
	}

}
