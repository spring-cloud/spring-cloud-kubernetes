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

import java.util.Objects;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.TMP_IMAGES;
import static org.springframework.cloud.kubernetes.integration.tests.commons.FixedIdNetworkProvider.createReusableNetwork;

/**
 * A K3sContainer, but with fixed port mappings. This is needed because of the nature of
 * some integration tests.
 *
 * @author wind57
 */
public final class FixedPortsK3sContainer extends K3sContainer {

	/**
	 * Test containers exposed ports.
	 */
	private static final int[] EXPOSED_PORTS = new int[] { 80, 6443, 8080, 8888, 9092, 32321, 32322 };

	/**
	 * Port for the local running images registry.
	 */
	public static final int REGISTRY_PORT = 6000;

	/**
	 * Small doc on how the set-up works. ( 5000 is just an example ) <pre>
	 *     - we start a local registry and expose it on localhost:<5000>
	 *     - from the host, we can push an image with:
	 *       docker push localhost:5000/image:tag
	 *     - the image is now stored in that local registry
	 *     - k3s later sees the image reference: localhost:5000/image:tag
	 *     - inside the k3s container, localhost would point to k3s itself, not to the registry
	 *     - because of the mirror entry in registries.yaml, when k3s/containerd sees
	 *       an image starting with localhost:5000, it actually uses the endpoint 'http://registry:5000'
	 *     - LocalRegistryContainer has withNetworkAliases("registry")
	 *     - this makes the registry reachable from the same Docker network via the hostname "registry"
	 * </pre>
	 */
	private static final Network NETWORK = createReusableNetwork("spring-cloud-kubernetes-local-docker-registry");

	private static final LocalRegistryContainer REGISTRY = new LocalRegistryContainer().configureFixedPorts()
		.withNetwork(NETWORK)
		.withNetworkAliases("registry")
		.withReuse(true);

	/**
	 * Rancher version to use for test-containers.
	 */
	private static final String RANCHER_VERSION = "rancher/k3s:v1.35.4-k3s1";

	/**
	 * Docker registry version.
	 */
	private static final String DOCKER_REGISTRY_VERSION = "registry:3.1.0";

	/**
	 * Command to use when starting rancher. Without "server" option, traefik is not
	 * installed
	 */
	private static final String RANCHER_COMMAND = "server --disable=metric-server --disable-default-registry-endpoint";

	static final K3sContainer CONTAINER = new FixedPortsK3sContainer(DockerImageName.parse(RANCHER_VERSION))
		.configureFixedPorts()
		.addBinds()
		.withCommand(RANCHER_COMMAND)
		.withReuse(true)
		.withNetwork(NETWORK)
		.withCopyFileToContainer(MountableFile.forClasspathResource("registries.yaml"),
				"/etc/rancher/k3s/registries.yaml");

	private FixedPortsK3sContainer(DockerImageName dockerImageName) {
		super(dockerImageName);
		REGISTRY.start();
	}

	private FixedPortsK3sContainer configureFixedPorts() {
		for (int port : EXPOSED_PORTS) {
			super.addFixedExposedPort(port, port);
		}
		return this;
	}

	private FixedPortsK3sContainer addBinds() {
		super.withCreateContainerCmdModifier(cmd -> {
			HostConfig hostConfig = Objects.requireNonNull(cmd.getHostConfig());
			hostConfig.withBinds(Bind.parse(TMP_IMAGES + ":" + TMP_IMAGES));
		});

		return this;
	}

	/**
	 * official registry image with fixed port 5000.
	 */
	private static final class LocalRegistryContainer extends GenericContainer<LocalRegistryContainer> {

		private LocalRegistryContainer() {
			super(DOCKER_REGISTRY_VERSION);
		}

		private LocalRegistryContainer configureFixedPorts() {
			addFixedExposedPort(REGISTRY_PORT, 5000);
			return this;
		}

	}

}
