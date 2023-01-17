/*
 * Copyright 2013-2022 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.SaveImageCmd;
import com.github.dockerjava.api.model.Image;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import static org.awaitility.Awaitility.await;

/**
 * A few commons things that can be re-used across clients. This is meant to be used for
 * testing purposes only.
 *
 * @author wind57
 */
public final class Commons {

	private Commons() {
		throw new AssertionError("No instance provided");
	}

	private static final String KUBERNETES_VERSION_FILE = "META-INF/springcloudkubernetes-version.txt";

	/**
	 * Rancher version to use for test-containers.
	 */
	public static final String RANCHER = "rancher/k3s:v1.25.4-k3s1";

	/**
	 * Command to use when starting rancher. Without "server" option, traefik is not
	 * installed
	 */
	public static final String RANCHER_COMMAND = "server";

	/**
	 * Test containers exposed ports.
	 */
	public static final int[] EXPOSED_PORTS = new int[] { 80, 6443, 8080, 8888, 9092 };

	/**
	 * Temporary folder where to load images.
	 */
	public static final String TEMP_FOLDER = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();

	private static final K3sContainer CONTAINER = new FixedPortsK3sContainer(DockerImageName.parse(Commons.RANCHER))
			.configureFixedPorts(EXPOSED_PORTS).withFileSystemBind(TEMP_FOLDER, TEMP_FOLDER)
			.withCommand(Commons.RANCHER_COMMAND).withReuse(true);

	public static K3sContainer container() {
		return CONTAINER;
	}

	public static void loadSpringCloudKubernetesImage(String project, K3sContainer container) throws Exception {
		loadImage("springcloud/" + project, pomVersion(), project, container);
	}

	/**
	 * assert that "left" is present and if so, "right" is not.
	 */
	public static void assertReloadLogStatements(String left, String right, String appLabel) {

		try {
			String appPodName = CONTAINER
					.execInContainer("kubectl", "get", "pods", "-l", "app=" + appLabel, "-o=name", "--no-headers")
					.getStdout();
			await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(240)).until(() -> {

				String allLogs = CONTAINER.execInContainer("kubectl", "logs", appPodName.trim()).getStdout();
				if (allLogs.contains(left)) {
					Assertions.assertFalse(allLogs.contains(right));
					return true;
				}
				return false;
			});
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public static void loadImage(String image, String tag, String tarName, K3sContainer container) throws Exception {
		// save image
		try (SaveImageCmd saveImageCmd = container.getDockerClient().saveImageCmd(image)) {
			InputStream imageStream = saveImageCmd.withTag(tag).exec();

			Path imagePath = Paths.get(TEMP_FOLDER + "/" + tarName + ".tar");
			Files.deleteIfExists(imagePath);
			Files.copy(imageStream, imagePath);
			// import image with ctr. this works because TEMP_FOLDER is mounted in the
			// container
			container.execInContainer("ctr", "i", "import", TEMP_FOLDER + "/" + tarName + ".tar");
		}

	}

	public static void cleanUp(String image, K3sContainer container) throws Exception {
		container.execInContainer("crictl", "rmi", "docker.io/springcloud/" + image + ":" + pomVersion());
		container.execInContainer("rm", TEMP_FOLDER + "/" + image + ".tar");
	}

	public static void cleanUpDownloadedImage(String image) throws Exception {
		CONTAINER.execInContainer("crictl", "rmi", image);
	}

	/**
	 * validates that the provided image does exist in the local docker registry.
	 */
	public static void validateImage(String image, K3sContainer container) {
		try (ListImagesCmd listImagesCmd = container.getDockerClient().listImagesCmd()) {
			List<Image> images = listImagesCmd.exec();
			images.stream()
					.filter(x -> Arrays.stream(x.getRepoTags() == null ? new String[] {} : x.getRepoTags())
							.anyMatch(y -> y.contains(image)))
					.findFirst().orElseThrow(() -> new IllegalArgumentException("Image : " + image
							+ " not build locally. " + "You need to build it first, and then run the test"));
		}
	}

	public static void pullImage(String image, String tag, K3sContainer container) throws InterruptedException {
		try (PullImageCmd pullImageCmd = container.getDockerClient().pullImageCmd(image)) {
			pullImageCmd.withTag(tag).start().awaitCompletion();
		}

	}

	public static String processExecResult(Container.ExecResult execResult) {
		if (execResult.getExitCode() != 0) {
			throw new RuntimeException("stdout=" + execResult.getStdout() + "\n" + "stderr=" + execResult.getStderr());
		}

		return execResult.getStdout();
	}

	public static String pomVersion() {
		try (InputStream in = new ClassPathResource(KUBERNETES_VERSION_FILE).getInputStream()) {
			String version = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
			if (StringUtils.hasText(version)) {
				version = version.trim();
			}
			return version;
		}
		catch (IOException e) {
			ReflectionUtils.rethrowRuntimeException(e);
		}
		// not reachable since exception rethrown at runtime
		return null;
	}

	/**
	 * A K3sContainer, but with fixed port mappings. This is needed because of the nature
	 * of some integration tests.
	 *
	 * @author wind57
	 */
	private static final class FixedPortsK3sContainer extends K3sContainer {

		private FixedPortsK3sContainer(DockerImageName dockerImageName) {
			super(dockerImageName);
		}

		private FixedPortsK3sContainer configureFixedPorts(int[] ports) {
			for (int port : ports) {
				super.addFixedExposedPort(port, port);
			}
			return this;
		}

	}

}
