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
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.SaveImageCmd;
import com.github.dockerjava.api.model.Image;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

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

	private static final Log LOG = LogFactory.getLog(Commons.class);

	/**
	 * istio version used in our integration tests.
	 */
	public static final String ISTIO_VERSION = "1.16.0";

	private static final String LOCAL_ISTIO_BIN_PATH = "istio-cli/istio-" + ISTIO_VERSION + "/bin";

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
			.withCopyFileToContainer(MountableFile.forClasspathResource(LOCAL_ISTIO_BIN_PATH + "/istioctl", 0744),
					"/tmp/istioctl")
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
			String appPodName = CONTAINER.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + appLabel + " -o=name --no-headers | tr -d '\n'").getStdout();
			LOG.info("appPodName : ->" + appPodName + "<-");
			// we issue a pollDelay to let the logs sync in, otherwise the results are not
			// going to be correctly asserted
			await().pollDelay(20, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(120))
					.until(() -> {

						Container.ExecResult result = CONTAINER.execInContainer("sh", "-c",
								"kubectl logs " + appPodName.trim() + "| grep " + "'" + left + "'");
						String error = result.getStderr();
						String ok = result.getStdout();

						LOG.info("error is : -->" + error + "<--");

						if (ok != null && !ok.isBlank()) {

							if (!right.isBlank()) {
								String notPresent = CONTAINER
										.execInContainer("sh", "-c",
												"kubectl logs " + appPodName.trim() + "| grep " + "'" + right + "'")
										.getStdout();
								Assertions.assertTrue(notPresent == null || notPresent.isBlank());
							}

							return true;
						}
						LOG.info("log statement not yet present");
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
			await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(1)).until(() -> {
				Container.ExecResult result = container.execInContainer("ctr", "i", "import",
						TEMP_FOLDER + "/" + tarName + ".tar");
				boolean noErrors = result.getStderr() == null || result.getStderr().isEmpty();
				if (!noErrors) {
					LOG.info("error is : " + result.getStderr());
				}
				return noErrors;
			});
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

	/**
	 * equivalent of 'docker system prune', but for crictl.
	 */
	public static void systemPrune() {
		try {
			CONTAINER.execInContainer("sh", "-c",
					"crictl ps -a | grep -v Running | awk '{print $1}' | xargs crictl rm && crictl rmi --prune");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
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
	 * the assumption is that there is only a single pod that is 'Running'.
	 */
	public static void waitForLogStatement(String message, K3sContainer k3sContainer, String appLabelValue) {
		try {

			await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(4)).until(() -> {

				String appPodName = k3sContainer.execInContainer("sh", "-c",
						"kubectl get pods -l app=" + appLabelValue
								+ " -o custom-columns=POD:metadata.name,STATUS:status.phase"
								+ " | grep -i 'running' | awk '{print $1}' | tr -d '\n' ")
						.getStdout();

				String execResult = k3sContainer.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim())
						.getStdout();
				return execResult.contains(message);
			});
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

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
