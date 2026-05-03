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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;
import org.springframework.core.annotation.AnnotatedElementUtils;

public final class NativeClientIntegrationTestExtension
		implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

	private static final String K8S_NATIVE_KUBERNETES_FIXTURE_NAME = NativeClientKubernetesFixture.class
		.getSimpleName();

	// store for our integration tests only.
	private static final ExtensionContext.Namespace K3S_STORE_NAMESPACE = ExtensionContext.Namespace
		.create(NativeClientKubernetesFixture.class);

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {

		NativeClientIntegrationTest scenario = nativeClientIntegrationTest(context);
		if (scenario == null) {
			return;
		}

		K3sContainer container = ensureK3sStarted();
		NativeClientKubernetesFixture nativeClientKubernetesFixture = nativeClientKubernetesFixture(context);

		// 1. create all namespaces
		for (String namespace : scenario.namespaces()) {
			nativeClientKubernetesFixture.createNamespace(namespace);
		}

		// 2. external image presence
		for (String image : scenario.withImages()) {
			Commons.validateImage(image, container);
			Commons.tagAndPushSpringCloudKubernetesImage(image, container);
		}

		// 3. set-up RBAC.
		for (String rbacNamespace : scenario.rbacNamespaces()) {
			nativeClientKubernetesFixture.setUp(rbacNamespace);
		}

		// 4. cluster wide RBAC
		if (scenario.clusterWideRBAC().enabled()) {
			nativeClientKubernetesFixture.setUpClusterWide(scenario.clusterWideRBAC().serviceAccountNamespace(),
					scenario.clusterWideRBAC().roleBindingNamespaces());
		}

		// 5. external name service
		if (scenario.deployExternalNameService()) {
			nativeClientKubernetesFixture.externalName(Phase.CREATE);
		}

		// 6. create busybox instances in proper namespaces
		if (scenario.busyboxNamespaces().length > 0) {
			Images.loadBusybox(container);
			for (String busyboxNamespace : scenario.busyboxNamespaces()) {
				nativeClientKubernetesFixture.busybox(busyboxNamespace, Phase.CREATE);
			}
		}

		// 7. create wiremock instances in proper namespaces
		if (scenario.wiremock().enabled()) {
			Images.loadWiremock(container);
			for (String wiremockNamespace : scenario.wiremock().namespaces()) {
				nativeClientKubernetesFixture.wiremock(wiremockNamespace, Phase.CREATE,
						scenario.wiremock().withNodePort());
			}
		}

		// 8. deploy configuration watcher.
		if (scenario.configurationWatcher().enabled()) {
			nativeClientKubernetesFixture.configWatcher(Phase.CREATE, scenario.configurationWatcher().refreshDelay(),
					scenario.configurationWatcher().reloadEnabled(), scenario.configurationWatcher().watchNamespaces(),
					scenario.configurationWatcher().kafkaEnabled(), scenario.configurationWatcher().rabbitMqEnabled());
		}

		// 9. deploy discovery server
		if (scenario.deployDiscoverServer()) {
			nativeClientKubernetesFixture.discoveryServer(Phase.CREATE);
		}

		// 10. deploy kafka
		if (scenario.deployKafka()) {
			nativeClientKubernetesFixture.kafka(Phase.CREATE);
		}

		// 11. deploy rabbitMq
		if (scenario.deployRabbitMq()) {
			nativeClientKubernetesFixture.rabbitMq(Phase.CREATE);
		}
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {

		NativeClientIntegrationTest scenario = nativeClientIntegrationTest(context);
		if (scenario == null) {
			return;
		}

		NativeClientKubernetesFixture nativeClientKubernetesFixture = nativeClientKubernetesFixture(context);

		// 1. delete wide RBAC
		if (scenario.clusterWideRBAC().enabled()) {
			nativeClientKubernetesFixture.deleteClusterWide(scenario.clusterWideRBAC().serviceAccountNamespace(),
					scenario.clusterWideRBAC().roleBindingNamespaces());
		}

		// 2. external name service
		if (scenario.deployExternalNameService()) {
			nativeClientKubernetesFixture.externalName(Phase.DELETE);
		}

		// 3. delete busybox instances in proper namespaces
		for (String busyboxNamespace : scenario.busyboxNamespaces()) {
			nativeClientKubernetesFixture.busybox(busyboxNamespace, Phase.DELETE);
		}

		// 4. delete wiremock instances in proper namespaces
		if (scenario.wiremock().enabled()) {
			for (String wiremockNamespace : scenario.wiremock().namespaces()) {
				nativeClientKubernetesFixture.wiremock(wiremockNamespace, Phase.DELETE, true);
			}
		}

		// 5. delete configuration watcher.
		if (scenario.configurationWatcher().enabled()) {
			nativeClientKubernetesFixture.configWatcher(Phase.DELETE, "", false, null, false, false);
		}

		// 6. delete all namespaces
		for (String namespace : scenario.namespaces()) {
			nativeClientKubernetesFixture.deleteNamespace(namespace);
		}

		// 7. delete discovery server
		if (scenario.deployDiscoverServer()) {
			nativeClientKubernetesFixture.discoveryServer(Phase.DELETE);
		}

		// 8. delete kafka
		if (scenario.deployKafka()) {
			nativeClientKubernetesFixture.kafka(Phase.DELETE);
		}

		// 9. delete rabbitMq
		if (scenario.deployRabbitMq()) {
			nativeClientKubernetesFixture.rabbitMq(Phase.DELETE);
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		// @Test(K8sNativeKubernetesFixture fixture) => check is we support such an
		// argument
		Class<?> testParameterType = parameterContext.getParameter().getType();
		return testParameterType == NativeClientKubernetesFixture.class || testParameterType == K3sContainer.class;
	}

	@Override
	public @Nullable Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		Class<?> testParameterType = parameterContext.getParameter().getType();

		// if 'supportsParameter' returns true, this method resolves the instance,
		// we get a single instance across all tests.
		if (testParameterType == NativeClientKubernetesFixture.class) {
			return nativeClientKubernetesFixture(extensionContext);
		}

		if (testParameterType == K3sContainer.class) {
			return ensureK3sStarted();
		}

		throw new ParameterResolutionException("Unsupported parameter type: " + testParameterType.getName());
	}

	private NativeClientKubernetesFixture nativeClientKubernetesFixture(ExtensionContext context) {
		return context.getRoot()
			.getStore(K3S_STORE_NAMESPACE)
			.computeIfAbsent(K8S_NATIVE_KUBERNETES_FIXTURE_NAME,
					key -> new NativeClientKubernetesFixture(ensureK3sStarted()), NativeClientKubernetesFixture.class);
	}

	/**
	 * check if test is annotated with @K8sNativeK3sIntegrationTest.
	 */
	private NativeClientIntegrationTest nativeClientIntegrationTest(ExtensionContext context) {
		return AnnotatedElementUtils.findMergedAnnotation(context.getRequiredTestClass(),
				NativeClientIntegrationTest.class);
	}

	private static K3sContainer ensureK3sStarted() {
		K3sContainer container = Commons.container();
		if (!container.isRunning()) {
			container.start();
		}
		return container;
	}

}
