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

package org.springframework.cloud.kubernetes.configuration.watcher;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;

/**
 * @author wind57
 */
enum KubernetesSource {

	CONFIGMAP {
		@Override
		String description() {
			return "configmap";
		}

		@Override
		String annotation() {
			return "spring.cloud.kubernetes.configmap.apps";
		}
	},
	SECRET {
		@Override
		String description() {
			return "secret";
		}

		@Override
		String annotation() {
			return "spring.cloud.kubernetes.secret.apps";
		}

	};

	abstract String description();

	abstract String annotation();

	static KubernetesSource fromK8sType(KubernetesObject kubernetesObject) {
		if (kubernetesObject instanceof V1ConfigMap) {
			return KubernetesSource.CONFIGMAP;
		}

		if (kubernetesObject instanceof V1Secret) {
			return KubernetesSource.SECRET;
		}

		throw new IllegalArgumentException("Unsupported: " + kubernetesObject.getClass());
	}

}
