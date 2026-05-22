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

	private Constants() {

	}

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
	 */
	static final String LOCAL_IMAGE_TARS_DIR = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();

	/**
	 * where is the version situated.
	 */
	static final String KUBERNETES_VERSION_FILE = "META-INF/springcloudkubernetes-version.txt";

}
