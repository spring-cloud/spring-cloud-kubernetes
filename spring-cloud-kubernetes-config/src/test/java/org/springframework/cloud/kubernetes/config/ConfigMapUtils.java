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

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

/**
 * @author Andres Navidad
 */
public class ConfigMapUtils {

	private final static String PATH_CONFIGMAP = "/api/v1/namespaces/%s/configmaps/%s";

	public static void createConfigmap(KubernetesServer server, String configMapName,
			String namespace, Map<String, String> data) {

		server.expect().withPath(String.format(PATH_CONFIGMAP, namespace, configMapName))
				.andReturn(200, new ConfigMapBuilder().withNewMetadata()
						.withName(configMapName).endMetadata().addToData(data).build())
				.always();
	}

}
