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

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.testcontainers.k3s.K3sContainer;

/**
 * @author wind57
 */
public final class Images {

	private static final String BUSYBOX = "busybox";

	private static final String BUSYBOX_TAR = BUSYBOX + ":" + busyboxVersion();

	private static final String WIREMOCK = "wiremock/wiremock";

	private static final String WIREMOCK_TAR = WIREMOCK.replace('/', '-') + ":" + wiremockVersion();

	private static final String ISTIOCTL = "istio/istioctl";

	private static final String ISTIOCTL_TAR = ISTIOCTL.replace('/', '-') + ":" + istioVersion();

	private static final String ISTIO_PROXY_V2 = "istio/proxyv2";

	private static final String ISTIO_PROXY_V2_TAR = ISTIO_PROXY_V2.replace('/', '-') + ":" + istioVersion();

	private static final String ISTIO_PILOT = "istio/pilot";

	private static final String ISTIO_PILOT_TAR = ISTIO_PILOT.replace('/', '-') + ":" + istioVersion();

	private static final String KAFKA = "confluentinc/cp-kafka";

	private static final String KAFKA_TAR = KAFKA.replace('/', '-') + kafkaVersion();

	private static final String RABBITMQ = "rabbitmq";

	private static final String RABBITMQ_TAR = "rabbitmq";

	private Images() {

	}

	public static String busyboxVersion() {
		return imageVersion(BUSYBOX);
	}

	public static String istioVersion() {
		return imageVersion(ISTIOCTL);
	}

	public static String kafkaVersion() {
		return imageVersion(KAFKA);
	}

	public static String rabbitMqVersion() {
		return imageVersion(RABBITMQ);
	}

	public static String wiremockVersion() {
		return imageVersion(WIREMOCK);
	}

	public static void loadBusybox(K3sContainer container) {
		Commons.load(container, BUSYBOX_TAR, BUSYBOX, busyboxVersion());
	}

	public static void loadWiremock(K3sContainer container) {
		Commons.load(container, WIREMOCK_TAR, WIREMOCK, wiremockVersion());
	}

	public static void loadIstioCtl(K3sContainer container) {
		Commons.load(container, ISTIOCTL_TAR, ISTIOCTL, istioVersion());
	}

	public static void loadIstioProxyV2(K3sContainer container) {
		Commons.load(container, ISTIO_PROXY_V2_TAR, ISTIO_PROXY_V2, istioVersion());
	}

	public static void loadIstioPilot(K3sContainer container) {
		Commons.load(container, ISTIO_PILOT_TAR, ISTIO_PILOT, istioVersion());
	}

	public static void loadKafka(K3sContainer container) {
		Commons.load(container, KAFKA_TAR, KAFKA, kafkaVersion());
	}

	public static void loadRabbitmq(K3sContainer container) {
		Commons.load(container, RABBITMQ_TAR, RABBITMQ, rabbitMqVersion());
	}

	// find the image version from current-images.txt
	private static String imageVersion(String imageNameForDownload) {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(Commons.class.getClassLoader().getResourceAsStream("current-images.txt")));
		return reader.lines()
			.filter(line -> line.contains(imageNameForDownload))
			.map(line -> line.split(":")[1])
			.findFirst()
			.orElseThrow();
	}

}
