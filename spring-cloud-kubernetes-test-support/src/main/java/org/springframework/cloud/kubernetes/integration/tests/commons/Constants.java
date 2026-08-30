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

/**
 * @author wind57
 */
final class Constants {

	/**
	 * System property that overrides the directory used to stage local image tar files.
	 */
	static final String LOCAL_IMAGE_TARS_DIR_PROPERTY = "spring.cloud.kubernetes.integration.tests.image-tars-dir";

	/**
	 * Environment variable equivalent of {@link #LOCAL_IMAGE_TARS_DIR_PROPERTY}.
	 */
	static final String LOCAL_IMAGE_TARS_DIR_ENV = "SPRING_CLOUD_KUBERNETES_IMAGE_TARS_DIR";

	/**
	 * Directory populated by the CI pipeline with prebuilt image tar files. It contains
	 * tar files for:
	 * <ul>
	 * <li>common test images (busybox, wiremock, etc.)</li>
	 * <li>controller images (configuration watcher, discovery server, config server)</li>
	 * <li>application images built from the integration-tests project</li>
	 * </ul>
	 */
	static final String CI_IMAGE_TARS_DIR = "/tmp/docker/images";

	/**
	 * Directory used during local runs to stage image tar files created from the local
	 * Docker cache before importing them into K3s with 'ctr i import'.
	 * <p>
	 * This directory is bind mounted into the K3s container, so it must be visible to the
	 * Docker daemon. Docker runtimes that run inside a virtual machine (colima, Rancher
	 * Desktop, minikube, ...) only share a subset of the host filesystem with that
	 * virtual machine, and 'java.io.tmpdir' (on macOS '/var/folders/...') is typically
	 * not part of it. In that case the bind mount silently resolves to an empty directory
	 * inside the virtual machine and 'ctr i import' fails with 'no such file or
	 * directory'. Such setups can point this directory to a shared location (for example
	 * one under the user home) with {@link #LOCAL_IMAGE_TARS_DIR_PROPERTY} or
	 * {@link #LOCAL_IMAGE_TARS_DIR_ENV}.
	 */
	static final String LOCAL_IMAGE_TARS_DIR = localImageTarsDir();

	/**
	 * where is the version situated.
	 */
	static final String KUBERNETES_VERSION_FILE = "META-INF/springcloudkubernetes-version.txt";

	private Constants() {

	}

	private static String localImageTarsDir() {
		String configured = System.getProperty(LOCAL_IMAGE_TARS_DIR_PROPERTY);
		if (configured == null || configured.isBlank()) {
			configured = System.getenv(LOCAL_IMAGE_TARS_DIR_ENV);
		}

		File dir = new File(
				configured == null || configured.isBlank() ? System.getProperty("java.io.tmpdir") : configured);

		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IllegalStateException("could not create image tars directory : " + dir.getAbsolutePath());
		}

		return dir.getAbsolutePath();
	}

}
