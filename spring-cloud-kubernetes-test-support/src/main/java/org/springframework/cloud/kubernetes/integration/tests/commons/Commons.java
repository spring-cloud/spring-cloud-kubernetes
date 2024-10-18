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

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.KUBERNETES_VERSION_FILE;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.TEMP_FOLDER;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.TMP_IMAGES;
import static org.springframework.cloud.kubernetes.integration.tests.commons.FixedPortsK3sContainer.CONTAINER;

/**
 * A few commons things that can be re-used across clients. This is meant to be used for
 * testing purposes only.
 *
 * @author wind57
 */
public final class Commons {

	private static String POM_VERSION;

	private static final Log LOG = LogFactory.getLog(Commons.class);

	private Commons() {
		throw new AssertionError("No instance provided");
	}

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
				.execInContainer("sh", "-c",
						"kubectl get pods -l app=" + appLabel + " -o=name --no-headers | tr -d '\n'")
				.getStdout();
			LOG.info("appPodName : ->" + appPodName + "<-");
			// we issue a pollDelay to let the logs sync in, otherwise the results are not
			// going to be correctly asserted
			await().pollDelay(20, TimeUnit.SECONDS)
				.pollInterval(Duration.ofSeconds(5))
				.atMost(Duration.ofSeconds(120))
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

	/**
	 * create a tar, copy it in the running k3s and load this tar as an image.
	 */
	public static void loadImage(String image, String tag, String tarName, K3sContainer container) throws Exception {

		if (imageAlreadyInK3s(container, tarName)) {
			return;
		}

		// save image
		try (SaveImageCmd saveImageCmd = container.getDockerClient().saveImageCmd(image)) {
			InputStream imageStream = saveImageCmd.withTag(tag).exec();

			Path imagePath = Paths.get(TEMP_FOLDER + "/" + tarName + ".tar");
			Files.copy(imageStream, imagePath, StandardCopyOption.REPLACE_EXISTING);
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

	/**
	 * either get the tar from '/tmp/docker/images', or pull the image.
	 */
	public static void load(K3sContainer container, String tarName, String imageNameForDownload, String imageVersion) {

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
			LOG.info("no tars found, will resort to pulling the image");
			LOG.info("using : " + imageVersion + " for : " + imageNameForDownload);
			pullImage(imageNameForDownload, imageVersion, tarName, container);
			loadImage(imageNameForDownload, imageVersion, tarName, container);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
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
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Image : " + image + " not build locally. "
						+ "You need to build it first, and then run the test"));
		}
	}

	public static void pullImage(String image, String tag, String tarName, K3sContainer container)
			throws InterruptedException {

		if (imageAlreadyInK3s(container, tarName)) {
			return;
		}

		try (PullImageCmd pullImageCmd = container.getDockerClient().pullImageCmd(image)) {
			pullImageCmd.withTag(tag).start().awaitCompletion();
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

			await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(4)).until(() -> {

				String appPodName = k3sContainer
					.execInContainer("sh", "-c",
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

	private static void loadImageFromPath(String tarName, K3sContainer container) {
		await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			Container.ExecResult result = container.execInContainer("ctr", "i", "import", TMP_IMAGES + "/" + tarName);
			boolean noErrors = result.getStderr() == null || result.getStderr().isEmpty();
			if (!noErrors) {
				LOG.info("error is : " + result.getStderr());
			}
			return noErrors;
		});
	}

	private static boolean imageAlreadyInK3s(K3sContainer container, String tarName) {
		try {
			boolean present = container.execInContainer("sh", "-c", "ctr images list | grep " + tarName)
				.getStdout()
				.contains(tarName);
			if (present) {
				System.out.println("image : " + tarName + " already in k3s, skipping");
				return true;
			}
			else {
				System.out.println("image : " + tarName + " not in k3s");
				return false;
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
