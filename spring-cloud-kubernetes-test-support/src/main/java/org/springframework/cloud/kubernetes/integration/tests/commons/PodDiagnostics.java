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

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;

/**
 * Dumps what Kubernetes knows about a deployment that did not become ready, so that a
 * failure in CI can be diagnosed from the build log alone. When a deployment never
 * becomes available, the only thing the tests log is "Available replicas: 0", which says
 * nothing about why (image pull errors, a crash looping container, a failing probe, ...).
 * Every step here is best effort: a problem collecting diagnostics is logged and never
 * replaces the original failure.
 *
 * @author Ryan Baxter
 */
public final class PodDiagnostics {

	private static final Log LOG = LogFactory.getLog(PodDiagnostics.class);

	/**
	 * Lines of logs to keep per container. Enough to hold the start of a crashing JVM
	 * (the stacktrace) together with whatever it prints on the way out.
	 */
	private static final int LOG_TAIL_LINES = 400;

	private PodDiagnostics() {

	}

	/**
	 * Log pods, the deployment, pod descriptions, current and previous container logs and
	 * events for the given deployment.
	 * @param container the k3s container to run kubectl in
	 * @param namespace namespace of the deployment
	 * @param deploymentName name of the deployment
	 * @param selectorLabels the labels the deployment selects its pods with, an empty (or
	 * null) map means all pods in the namespace
	 */
	public static void dump(K3sContainer container, String namespace, String deploymentName,
			Map<String, String> selectorLabels) {

		String ns = shellQuote(namespace);
		String selector = labelSelector(selectorLabels);
		String podSelector = selector.isEmpty() ? "" : " -l " + shellQuote(selector);

		LOG.warn("deployment '" + deploymentName + "' in namespace '" + namespace
				+ "' did not become ready, collecting diagnostics");

		run(container, "pods", "kubectl get pods -n " + ns + " -o wide");
		run(container, "deployment", "kubectl describe deployment " + shellQuote(deploymentName) + " -n " + ns);
		run(container, "pod descriptions", "kubectl describe pods -n " + ns + podSelector);
		// logs are fetched pod by pod, so that a pod without a previous container (or one
		// that is still starting) does not prevent us from reading the logs of the others
		run(container, "pod logs",
				"for p in $(kubectl get pods -n " + ns + podSelector + " -o name); do "
						+ "echo \"===== $p (current)\"; " + "kubectl logs -n " + ns + " \"$p\" --all-containers --tail="
						+ LOG_TAIL_LINES + "; " + "echo \"===== $p (previous)\"; " + "kubectl logs -n " + ns
						+ " \"$p\" --all-containers " + "--previous --tail=" + LOG_TAIL_LINES + "; done");
		run(container, "events", "kubectl get events -n " + ns + " --sort-by=.lastTimestamp");
	}

	private static void run(K3sContainer container, String what, String command) {
		try {
			// stderr is merged into stdout, kubectl reports most useful problems there
			Container.ExecResult result = container.execInContainer("sh", "-c", command + " 2>&1");
			LOG.warn("diagnostics, " + what + " (exit code " + result.getExitCode() + "):\n" + result.getStdout());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			LOG.warn("interrupted while collecting diagnostics: " + what);
		}
		catch (Exception ex) {
			LOG.warn("could not collect diagnostics: " + what, ex);
		}
	}

	private static String labelSelector(Map<String, String> labels) {
		if (labels == null || labels.isEmpty()) {
			return "";
		}
		return new TreeMap<>(labels).entrySet()
			.stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(Collectors.joining(","));
	}

	private static String shellQuote(String value) {
		return "'" + value.replace("'", "'\\''") + "'";
	}

}
