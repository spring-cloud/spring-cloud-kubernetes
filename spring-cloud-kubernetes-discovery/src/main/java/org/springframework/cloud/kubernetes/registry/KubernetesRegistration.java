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

package org.springframework.cloud.kubernetes.registry;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryProperties;

/**
 * Kubernetes implementation of a {@link Registration}.
 *
 * @author Mauricio Salatino
 */
public class KubernetesRegistration implements Registration, Closeable {

	private final KubernetesClient client;

	private KubernetesDiscoveryProperties properties;

	private AtomicBoolean running = new AtomicBoolean(false);

	public KubernetesRegistration(KubernetesClient client, KubernetesDiscoveryProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public void close() throws IOException {
		this.client.close();
	}

	@Override
	public String getServiceId() {
		return this.properties.getServiceName();
	}

	@Override
	public String getHost() {
		return this.client.getMasterUrl().getHost();
	}

	@Override
	public int getPort() {
		return 0;
	}

	@Override
	public boolean isSecure() {
		return false;
	}

	@Override
	public URI getUri() {
		try {
			return this.client.getMasterUrl().toURI();
		}
		catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	public KubernetesDiscoveryProperties getProperties() {
		return this.properties;
	}

	@Override
	public Map<String, String> getMetadata() {
		return null;
	}

	@Override
	public String toString() {
		return "KubernetesRegistration{" + "client=" + this.client + ", properties=" + this.properties + ", running="
				+ this.running + '}';
	}

}
