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

package org.springframework.cloud.kubernetes.integration.tests.commons.k3s;

import java.io.InputStream;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Fabric8KubernetesFixture;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * @author wind57
 */
public final class Fabric8K3sIntegrationTestExtension
		implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

	private static final String FABRIC8_KUBERNETES_FIXTURE_NAME = Fabric8KubernetesFixture.class.getSimpleName();

	// store for our integration tests only.
	private static final ExtensionContext.Namespace K3S_STORE_NAMESPACE = ExtensionContext.Namespace
		.create(Fabric8K3sIntegrationTestExtension.class);

	@Override
	public void beforeAll(ExtensionContext context) {
		K3sIntegrationTest scenario = k3sIntegrationTest(context);
		if (scenario == null) {
			return;
		}

		K3sContainer container = ensureK3sStarted();
		Fabric8KubernetesFixture fabric8KubernetesFixture = fabric8KubernetesFixture(context);

		// 1. create all namespaces
		for (String namespace : scenario.namespaces()) {
			fabric8KubernetesFixture.createNamespace(namespace);
		}

		// 2. external image presence
		for (String image : scenario.withImages()) {
			Commons.validateImage(image, container);
			Commons.loadSpringCloudKubernetesImage(image, container);
		}

		// 3. deploy istio
		if (scenario.deployIstio()) {
			istioSetup(container, fabric8KubernetesFixture);
		}

		// 4. create busybox instances in proper namespaces
		if (scenario.busyboxNamespaces().length > 0) {
			Images.loadBusybox(container);
			for (String busyboxNamespace : scenario.busyboxNamespaces()) {
				fabric8KubernetesFixture.busybox(busyboxNamespace, Phase.CREATE);
			}
		}

		// 5. create wiremock instances in proper namespaces
		if (scenario.wiremockNamespaces().length > 0) {
			Images.loadWiremock(container);
			for (String wiremockNamespace : scenario.wiremockNamespaces()) {
				fabric8KubernetesFixture.wiremock(wiremockNamespace, Phase.CREATE, false);
			}
		}

		// 6. set-up RBAC.
		for (String rbacNamespace : scenario.rbacNamespaces()) {
			fabric8KubernetesFixture.setUp(rbacNamespace);
		}

		// 7. deploy external-name-service
		if (scenario.deployExternalNameService()) {
			fabric8KubernetesFixture.externalName(Phase.CREATE);
		}

		// 8. deploy configuration watcher.
		if (scenario.deployConfigurationWatcher()) {
			fabric8KubernetesFixture.configWatcher(Phase.CREATE);
		}

	}

	@Override
	public void afterAll(ExtensionContext context) {
		K3sIntegrationTest scenario = k3sIntegrationTest(context);
		if (scenario == null) {
			return;
		}

		Fabric8KubernetesFixture fabric8KubernetesFixture = fabric8KubernetesFixture(context);

		// 1. delete istio
		if (scenario.deployIstio()) {
			fabric8KubernetesFixture.istioCtl("istio-test", Phase.DELETE);
		}

		// 2. delete busybox instances in proper namespaces
		for (String busyboxNamespace : scenario.busyboxNamespaces()) {
			fabric8KubernetesFixture.busybox(busyboxNamespace, Phase.DELETE);
		}

		// 3. delete wiremock instances in proper namespaces
		for (String wiremockNamespace : scenario.wiremockNamespaces()) {
			fabric8KubernetesFixture.wiremock(wiremockNamespace, Phase.DELETE, false);
		}

		// 4. delete external-name-service
		if (scenario.deployExternalNameService()) {
			fabric8KubernetesFixture.externalName(Phase.DELETE);
		}

		// 5. delete configuration watcher.
		if (scenario.deployConfigurationWatcher()) {
			fabric8KubernetesFixture.configWatcher(Phase.DELETE);
		}

		// 6. delete all namespaces
		for (String namespace : scenario.namespaces()) {
			fabric8KubernetesFixture.deleteNamespace(namespace);
		}

	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {

		// @Test(Fabric8KubernetesFixture fixture) => check is we support such an argument
		Class<?> testParameterType = parameterContext.getParameter().getType();
		return testParameterType == Fabric8KubernetesFixture.class;
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {

		Class<?> testParameterType = parameterContext.getParameter().getType();

		// if 'supportsParameter' returns true, this method resolves the instance,
		// we get a single instance across all tests.
		if (testParameterType == Fabric8KubernetesFixture.class) {
			return fabric8KubernetesFixture(extensionContext);
		}

		throw new ParameterResolutionException("Unsupported parameter type: " + testParameterType.getName());
	}

	private Fabric8KubernetesFixture fabric8KubernetesFixture(ExtensionContext context) {
		return context.getRoot()
			.getStore(K3S_STORE_NAMESPACE)
			.computeIfAbsent(FABRIC8_KUBERNETES_FIXTURE_NAME, key -> new Fabric8KubernetesFixture(ensureK3sStarted()),
					Fabric8KubernetesFixture.class);
	}

	/**
	 * check if test is annotated with @K3sIntegrationTest.
	 */
	private K3sIntegrationTest k3sIntegrationTest(ExtensionContext context) {
		return AnnotatedElementUtils.findMergedAnnotation(context.getRequiredTestClass(), K3sIntegrationTest.class);
	}

	private static K3sContainer ensureK3sStarted() {
		K3sContainer container = Commons.container();
		if (!container.isRunning()) {
			container.start();
		}
		return container;
	}

	private void istioSetup(K3sContainer container, Fabric8KubernetesFixture fabric8KubernetesFixture) {
		try {

			Images.loadIstioCtl(container);
			Images.loadIstioProxyV2(container);
			Images.loadIstioPilot(container);

			processExecResult(container.execInContainer("sh", "-c",
					"kubectl label namespace istio-test istio-injection=enabled"));
			fabric8KubernetesFixture.istioCtl("istio-test", Phase.CREATE);

			String istioctlPodName = fabric8KubernetesFixture.istioctlPodName();

			processExecResult(container.execInContainer("sh", "-c",
					"kubectl cp istio-test/" + istioctlPodName + ":/usr/local/bin/istioctl /tmp/istioctl"));

			processExecResult(container.execInContainer("sh", "-c", "chmod +x /tmp/istioctl"));

			processExecResult(container.execInContainer("sh", "-c",
					"/tmp/istioctl" + " --kubeconfig=/etc/rancher/k3s/k3s.yaml install --set profile=minimal -y"));

			fabric8KubernetesFixture.setUpIstio("istio-test");

			InputStream deploymentStream = fabric8KubernetesFixture.inputStream("istio-deployment.yaml");
			InputStream serviceStream = fabric8KubernetesFixture.inputStream("istio-service.yaml");

			Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);
			Service service = Serialization.unmarshal(serviceStream, Service.class);

			fabric8KubernetesFixture.createAndWait("istio-test", null, deployment, service, true);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String processExecResult(Container.ExecResult execResult) {
		if (execResult.getExitCode() != 0) {
			throw new RuntimeException("stdout=" + execResult.getStdout() + "\n" + "stderr=" + execResult.getStderr());
		}

		return execResult.getStdout();
	}

}
