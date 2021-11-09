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

package org.springframework.cloud.kubernetes.configserver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public interface KubernetesPropertySourceSupplier {

	List<MapPropertySource> get(CoreV1Api coreV1Api, String name, String namespace, Environment environment);

	static List<String> namespaceSplitter(String namespacesString, String currentNamespace) {
		List<String> namespaces = Collections.singletonList(currentNamespace);
		String[] namespacesArray = StringUtils.commaDelimitedListToStringArray(namespacesString);
		if (namespacesArray.length > 0) {
			namespaces = Arrays.asList(namespacesArray);
		}
		return namespaces;
	}

}
