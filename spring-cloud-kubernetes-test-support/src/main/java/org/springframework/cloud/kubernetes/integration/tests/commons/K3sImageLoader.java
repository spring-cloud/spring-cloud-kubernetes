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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.SaveImageCmd;
import com.github.dockerjava.api.model.Image;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.CI_IMAGE_TARS_DIR;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.LOCAL_IMAGE_TARS_DIR;

/**
 * @author wind57
 */
public final class K3sImageLoader {

	private static final Log LOG = LogFactory.getLog(K3sImageLoader.class);

	private static final String DOCKER_IO = "docker.io/";

	private static final String DOCKER_IO_LIBRARY = DOCKER_IO + "library/";

	private K3sImageLoader() {

	}

	public static void loadSpringCloudKubernetesImage(String imageNameWithoutTag, K3sContainer container) {
		String tag = pomVersion();
		String imageNameWithTag = imageNameWithoutTag + ":" + tag;
		String tarFileName = imageNameWithTag + ".tar";
		Path ciTarPath = Paths.get(CI_IMAGE_TARS_DIR, tarFileName);

		// we are in github ci pipeline
		if (Files.exists(ciTarPath)) {
			loadCiImageTar(tarFileName, container);
			return;
		}

		createAndLoadLocalImageTar("springcloud/" + imageNameWithoutTag, tag, tarFileName, container);
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

		File dockerImagesRootDir = Paths.get(CI_IMAGE_TARS_DIR).toFile();
		if (dockerImagesRootDir.exists() && dockerImagesRootDir.isDirectory()) {
			File[] tars = dockerImagesRootDir.listFiles();
			if (tars != null && tars.length > 0) {
				Optional<String> found = Arrays.stream(tars)
					.map(File::getName)
					.filter(x -> x.contains(tarName))
					.findFirst();
				if (found.isPresent()) {
					LOG.info("running in github actions, will load from : " + CI_IMAGE_TARS_DIR + " tar : "
							+ found.get());
					loadCiImageTar(found.get(), container);
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
	 * validates that the provided image does exist in the local docker registry. This is
	 * only needed when running tests locally, in github actions images are already built
	 * by the pipeline.
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

	public static void pullImage(String imageWithTag, String tarName, K3sContainer container)
			throws InterruptedException {

		if (imageAlreadyInK3s(container, tarName)) {
			return;
		}

		try (PullImageCmd pullImageCmd = container.getDockerClient().pullImageCmd(imageWithTag)) {
			pullImageCmd.start().awaitCompletion();
		}
	}

	private static void loadCiImageTar(String tarFileName, K3sContainer container) {
		loadImageTar(CI_IMAGE_TARS_DIR, tarFileName, container);
	}

	private static void loadLocalImageTar(String tarFileName, K3sContainer container) {
		loadImageTar(LOCAL_IMAGE_TARS_DIR, tarFileName, container);
	}

	private static void loadImageTar(String imageTarsDir, String tarFileName, K3sContainer container) {
		Awaitilities.awaitUntil(120, 1000, () -> {
			Container.ExecResult result;
			try {
				result = container.execInContainer("ctr", "i", "import", imageTarsDir + "/" + tarFileName);
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

	/**
	 * Local fallback for Spring Cloud images: create a tar from the local Docker image if
	 * needed, then import it into K3s.
	 */
	private static void createAndLoadLocalImageTar(String imageNameWithoutTag, String tag, String tarFileName,
			K3sContainer container) {

		String imageRef = imageNameWithoutTag + ":" + tag;
		if (imageAlreadyInK3s(container, imageRef)) {
			return;
		}

		Path tarPath = Paths.get(LOCAL_IMAGE_TARS_DIR, tarFileName);

		if (Files.notExists(tarPath)) {
			try (SaveImageCmd saveImageCmd = container.getDockerClient().saveImageCmd(imageNameWithoutTag);
					InputStream imageStream = saveImageCmd.withTag(tag).exec()) {
				Files.copy(imageStream, tarPath, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		loadLocalImageTar(tarFileName, container);
	}

	private static boolean imageAlreadyInK3s(K3sContainer container, String tarName) {

		if (tarName == null) {
			return false;
		}

		try {
			boolean present = container.execInContainer("sh", "-c", "ctr images list | grep " + tarName)
				.getStdout()
				.contains(tarName);
			if (present) {
				LOG.info("image : " + tarName + " already in k3s, skipping");
				return true;
			}
			else {
				LOG.info("image : " + tarName + " not in k3s");
				return false;
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
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

}
