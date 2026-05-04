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

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.Image;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.KUBERNETES_VERSION_FILE;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.TMP_IMAGES;
import static org.springframework.cloud.kubernetes.integration.tests.commons.FixedPortsK3sContainer.CONTAINER;
import static org.springframework.cloud.kubernetes.integration.tests.commons.FixedPortsK3sContainer.REGISTRY_PORT;

/**
 * A few commons things that can be re-used across clients. This is meant to be used for
 * testing purposes only.
 *
 * @author wind57
 */
public final class Commons {

	private static String POM_VERSION;

	private static final Log LOG = LogFactory.getLog(Commons.class);

	private static final String DOCKER_IO = "docker.io/";

	private static final String DOCKER_IO_LIBRARY = DOCKER_IO + "library/";

	private Commons() {
		throw new AssertionError("No instance provided");
	}

	public static K3sContainer container() {
		return CONTAINER;
	}

	public static void tagAndPushSpringCloudKubernetesImageToLocalDockerRegistry(String imageNameWithoutTag,
			K3sContainer container) {
		try {
			String springCloudImageWithTag = "springcloud/" + imageNameWithoutTag + ":" + pomVersion();
			tagAndPushImage(springCloudImageWithTag, container);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * tag image and push to local registry.
	 */
	public static void tagAndPushImage(String imageNameWithTag, K3sContainer container) {

		if (imageAlreadyInK3s(container, imageNameWithTag)) {
			return;
		}

		try {
			int lastColon = imageNameWithTag.lastIndexOf(':');
			if (lastColon < 0) {
				throw new IllegalArgumentException("image must include tag: " + imageNameWithTag);
			}

			String imageWithoutTag = imageNameWithTag.substring(0, lastColon);
			String tag = imageNameWithTag.substring(lastColon + 1);

			String targetRepository = "localhost:" + REGISTRY_PORT + "/" + imageWithoutTag;
			String targetImageWithTag = targetRepository + ":" + tag;

			container.getDockerClient().tagImageCmd(imageNameWithTag, targetRepository, tag).exec();

			Awaitilities.awaitUntil(120, 1000, () -> {
				try {
					container.getDockerClient().pushImageCmd(targetImageWithTag).start().awaitCompletion();
					return true;
				}
				catch (Exception e) {
					LOG.info("failed to push image " + targetImageWithTag + " to local registry", e);
					return false;
				}
			});
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ensures a common external test image is available inside K3s/containerd. It first
	 * checks whether the image is already present in K3s. If not, it tries to load it as
	 * a tar under '/tmp/docker/images'. If no matching tar is found, it pulls the image
	 * directly inside K3s using 'ctr images pull'. This is meant for shared test images
	 * such as busybox, wiremock, kafka, etc.
	 */
	public static void loadOrPullCommonTestImages(K3sContainer container, String tarName, String imageNameForDownload,
			String imageVersion) {

		if (imageAlreadyInK3s(container, tarName)) {
			return;
		}

		File dockerImagesRootDir = Paths.get(TMP_IMAGES).toFile();
		if (dockerImagesRootDir.exists() && dockerImagesRootDir.isDirectory()) {
			File[] tars = dockerImagesRootDir.listFiles();
			if (tars != null && tars.length > 0) {
				Optional<String> found = Arrays.stream(tars)
					.map(File::getName)
					.filter(x -> x.contains(tarName))
					.findFirst();
				if (found.isPresent()) {
					LOG.info("running in github actions, will load from : " + TMP_IMAGES + " tar : " + found.get());
					loadImageFromPath(found.get(), container);
					return;
				}
				else {
					LOG.info(tarName + " not found, resorting to pulling the image");
				}
			}
			else {
				LOG.info("no tars found, will resort to pulling the image");
			}
		}
		else {
			LOG.info("running outside github actions");
		}

		try {
			LOG.info("pulling image inside k3s to avoid Docker save/ctr import compatibility issues");
			LOG.info("using : " + imageVersion + " for : " + imageNameForDownload);
			pullImageInsideK3s(container, imageNameForDownload, imageVersion);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Pull image directly inside the K3s container via ctr (containerd). This avoids
	 * Docker save + ctr import, which can fail with "content digest not found" for
	 * multi-platform or OCI images. If DOCKER_HUB_USERNAME and DOCKER_HUB_PASSWORD are
	 * set, they are passed as {@code --user username:password} for registry auth.
	 */
	private static void pullImageInsideK3s(K3sContainer container, String imageNameForDownload, String imageVersion) {
		String fullImageRef = fullImageReference(imageNameForDownload, imageVersion);

		final String[] ctrArgs = buildCtrPullArgs(fullImageRef);
		Awaitilities.awaitUntil(120, 1, () -> {
			try {
				Container.ExecResult result = container.execInContainer(ctrArgs);
				boolean noErrors = result.getStderr() == null || result.getStderr().isEmpty();
				if (!noErrors) {
					LOG.info("ctr pull stderr: " + result.getStderr());
				}
				return noErrors;
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}

		});
	}

	private static String[] buildCtrPullArgs(String fullImageRef) {
		String username = System.getenv("DOCKER_HUB_USERNAME");
		String password = System.getenv("DOCKER_HUB_PASSWORD");
		if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
			LOG.info("pulling inside k3s with Docker Hub credentials: " + fullImageRef);
			return new String[] { "ctr", "-n", "k8s.io", "images", "pull", "--user", username + ":" + password,
					fullImageRef };
		}
		LOG.info("pulling inside k3s: " + fullImageRef);
		return new String[] { "ctr", "-n", "k8s.io", "images", "pull", fullImageRef };
	}

	private static String fullImageReference(String imageName, String imageVersion) {
		String imageNameAndVersion = imageName + ":" + imageVersion;
		if (imageName.contains("/")) {
			return DOCKER_IO + imageNameAndVersion;
		}
		return DOCKER_IO_LIBRARY + imageNameAndVersion;
	}

	/**
	 * validates that the provided image does exist in the local dcoker cache.
	 */
	public static void validateImage(String image, K3sContainer container) {
		try (ListImagesCmd listImagesCmd = container.getDockerClient().listImagesCmd()) {
			List<Image> images = listImagesCmd.exec();
			images.stream()
				.filter(x -> Arrays.stream(x.getRepoTags() == null ? new String[] {} : x.getRepoTags())
					.anyMatch(y -> y.contains(image)))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Image : " + image + " not build locally. "
						+ "You need to build it first, and then run the test"));
		}
	}

	public static void pullImage(String imageFromDeployment, K3sContainer container) throws InterruptedException {

		if (imageAlreadyInK3s(container, imageFromDeployment)) {
			return;
		}

		try (PullImageCmd pullImageCmd = container.getDockerClient().pullImageCmd(imageFromDeployment)) {
			pullImageCmd.start().awaitCompletion();
		}
	}

	public static String pomVersion() {
		if (POM_VERSION == null) {
			try (InputStream in = new ClassPathResource(KUBERNETES_VERSION_FILE).getInputStream()) {
				String version = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
				if (StringUtils.hasText(version)) {
					POM_VERSION = version.trim();
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		return POM_VERSION;
	}

	/**
	 * the assumption is that there is only a single pod that is 'Running'.
	 */
	public static void waitForLogStatement(String message, K3sContainer k3sContainer, String appLabelValue) {
		try {

			Awaitilities.awaitUntil(120, 1000, () -> {

				try {
					String appPodName = k3sContainer
						.execInContainer("sh", "-c",
								"kubectl get pods -l app=" + appLabelValue
										+ " -o custom-columns=POD:metadata.name,STATUS:status.phase"
										+ " | grep -i 'running' | awk '{print $1}' | tr -d '\n' ")
						.getStdout();

					String execResult = k3sContainer.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim())
						.getStdout();
					return execResult.contains(message);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	public static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	private static void loadImageFromPath(String tarName, K3sContainer container) {
		Awaitilities.awaitUntil(120, 1000, () -> {
			Container.ExecResult result;
			try {
				result = container.execInContainer("ctr", "i", "import", Constants.TMP_IMAGES + "/" + tarName);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			boolean noErrors = result.getStderr() == null || result.getStderr().isEmpty();
			if (!noErrors) {
				LOG.info("error is : " + result.getStderr());
			}
			return noErrors;
		});
	}

	private static boolean imageAlreadyInK3s(K3sContainer container, String imageWithTag) {
		try {
			String stdout = container.execInContainer("ctr", "-n", "k8s.io", "images", "list", "-q").getStdout();

			boolean present = Arrays.stream(stdout.split("\\R"))
				.map(String::trim)
				.anyMatch(line -> line.contains(imageWithTag));

			if (present) {
				LOG.info("image : " + imageWithTag + " already in k3s, skipping");
				return true;
			}

			LOG.info("image : " + imageWithTag + " not in k3s");
			return false;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
