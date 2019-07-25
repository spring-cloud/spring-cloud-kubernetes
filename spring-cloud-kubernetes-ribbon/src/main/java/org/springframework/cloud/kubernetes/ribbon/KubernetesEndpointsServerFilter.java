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

package org.springframework.cloud.kubernetes.ribbon;

import com.netflix.client.config.IClientConfigKey;
import io.fabric8.kubernetes.api.model.EndpointAddress;

/**
 * Kubernetes implementation of a Ribbon {@link IClientConfigKey}.
 *
 * @param <T> type of key
 * @author fuheping
 */
public interface KubernetesEndpointsServerFilter {

	/**
	 * @param serviceId serviceName
	 * @param adress EndpointAddress
	 * @return is used for ribbon
	 */
	boolean isFilter(String serviceId, EndpointAddress adress);

}
