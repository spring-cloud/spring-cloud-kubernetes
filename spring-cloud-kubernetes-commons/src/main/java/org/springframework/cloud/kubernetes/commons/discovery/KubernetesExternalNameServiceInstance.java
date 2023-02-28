/*
 * Copyright 2019-2023 the original author or authors.
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

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;

/**
 * Type of {@link org.springframework.cloud.client.ServiceInstance} when
 * "spec.type=ExternalName".
 *
 * @author wind57
 */
public record KubernetesExternalNameServiceInstance(String serviceId, String host, String instanceId,
		Map<String, String> metadata) implements ServiceInstance {

	@Override
	public String getServiceId() {
		return serviceId;
	}

	@Override
	public String getHost() {
		return host;
	}

	@Override
	public int getPort() {
		return -1;
	}

	@Override
	public boolean isSecure() {
		return false;
	}

	@Override
	public URI getUri() {
		return URI.create(host);
	}

	@Override
	public Map<String, String> getMetadata() {
		return metadata;
	}

	public String getInstanceId() {
		return instanceId;
	}

	public String type() {
		return "ExternalName";
	}

}
