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

package org.springframework.cloud.kubernetes.integration.tests.commons;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;

/**
 * re-use implementation for a Network. It first checks if such a named network already
 * exists via docker client API and creates a new one if it does not.
 *
 * @author wind57
 */
final class FixedIdNetworkProvider {

	private FixedIdNetworkProvider() {

	}

	static Network createReusableNetwork(String name) {
		var client = DockerClientFactory.instance().client();

		String id = client.listNetworksCmd()
			.exec()
			.stream()
			.filter(network -> name.equals(network.getName()))
			.map(com.github.dockerjava.api.model.Network::getId)
			.findFirst()
			.orElseGet(() -> client.createNetworkCmd().withName(name).withCheckDuplicate(true).exec().getId());

		return new org.testcontainers.containers.Network() {
			@Override
			public String getId() {
				return id;
			}

			@Override
			public void close() {
				// intentionally no-op
			}
		};
	}

}
