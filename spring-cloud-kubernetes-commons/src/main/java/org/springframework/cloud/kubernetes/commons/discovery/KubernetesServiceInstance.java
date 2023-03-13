/*
 * Copyright 2019-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;

/**
 * @author wind57
 *
 * {@link ServiceInstance} with additional methods, specific to kubernetes.
 */
sealed public interface KubernetesServiceInstance extends ServiceInstance permits DefaultKubernetesServiceInstance {

	String getNamespace();

	String getCluster();

	default Map<String, Map<String, String>> podMetadata() {
		return Map.of();
	}

}
