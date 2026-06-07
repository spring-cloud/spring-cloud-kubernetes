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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

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

import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Constants.KUBERNETES_VERSION_FILE;
import static org.springframework.cloud.kubernetes.integration.tests.commons.FixedPortsK3sContainer.CONTAINER;

/**
 * A few commons things that can be re-used across clients. This is meant to be used for
 * testing purposes only.
 *
 * @author wind57
 */
public final class Commons {

	private static final Log LOG = LogFactory.getLog(Commons.class);

	private static String POM_VERSION;

	private Commons() {
		throw new AssertionError("No instance provided");
	}

	public static K3sContainer container() {
		return CONTAINER;
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

			awaitUntil(120, 1000, () -> {

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

	/**
	 * Wait until a Kubernetes service name resolves from inside the given pod.
	 * Uses {@code kubectl exec} in the specified namespace and retries
	 * {@code getent hosts <serviceName>} until it succeeds.
	 *
	 * A successful lookup returns output similar to:
	 * {@code 10.43.123.45 rabbitmq-service.default.svc.cluster.local rabbitmq-service}
	 *
	 * @param container the K3s test container used to run kubectl
	 * @param namespace the namespace that contains the pod
	 * @param podName the pod from which DNS resolution is tested
	 * @param serviceName the Kubernetes service name expected to resolve
	 */
	public static void waitUntilServiceResolves(K3sContainer container, String namespace, String podName,
			String serviceName) {
		awaitUntil(120, 3000, () -> {
			try {
				Container.ExecResult result = container.execInContainer(
					"kubectl", "exec", "-n", namespace, podName, "--", "getent", "hosts", serviceName);
				if (result.getExitCode() != 0) {
					LOG.warn("Service " + serviceName + " not resolved yet");
					return false;
				}
				else {
					return true;
				}
			}
			catch (Exception e) {
				LOG.error(e.getMessage(), e);
				return false;
			}
		});
	}

	public static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	public static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
