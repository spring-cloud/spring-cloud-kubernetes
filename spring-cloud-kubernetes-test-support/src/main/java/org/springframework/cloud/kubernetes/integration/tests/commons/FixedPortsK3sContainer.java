/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.Objects;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.TEMP_FOLDER;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.TMP_IMAGES;

/**
 * A K3sContainer, but with fixed port mappings. This is needed because of the nature of
 * some integration tests.
 *
 * @author wind57
 */
final class FixedPortsK3sContainer extends K3sContainer {

	/**
	 * Test containers exposed ports.
	 */
	private static final int[] EXPOSED_PORTS = new int[] { 80, 6443, 8080, 8888, 9092, 32321, 32322 };

	/**
	 * Rancher version to use for test-containers.
	 */
	private static final String RANCHER_VERSION = "rancher/k3s:v1.28.8-k3s1";

	/**
	 * Command to use when starting rancher. Without "server" option, traefik is not
	 * installed
	 */
	private static final String RANCHER_COMMAND = "server --disable=metric-server";

	static final K3sContainer CONTAINER = new FixedPortsK3sContainer(DockerImageName.parse(RANCHER_VERSION))
		.configureFixedPorts()
		.addBinds()
		.withCommand(RANCHER_COMMAND)
		.withReuse(true);

	FixedPortsK3sContainer(DockerImageName dockerImageName) {
		super(dockerImageName);
	}

	FixedPortsK3sContainer configureFixedPorts() {
		for (int port : EXPOSED_PORTS) {
			super.addFixedExposedPort(port, port);
		}
		return this;
	}

	FixedPortsK3sContainer addBinds() {
		super.withCreateContainerCmdModifier(cmd -> {
			HostConfig hostConfig = Objects.requireNonNull(cmd.getHostConfig());
			hostConfig.withBinds(Bind.parse(TEMP_FOLDER + ":" + TEMP_FOLDER),
					Bind.parse(TMP_IMAGES + ":" + TMP_IMAGES));
		});

		return this;
	}

}
