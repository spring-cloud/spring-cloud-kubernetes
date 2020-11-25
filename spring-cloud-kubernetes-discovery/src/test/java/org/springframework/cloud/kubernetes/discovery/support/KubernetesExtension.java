/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * @author Tim Ysewyn
 */
public class KubernetesExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

	private final KubernetesServer mockServer = new KubernetesServer();

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
		return (parameterContext.getParameter().isAnnotationPresent(Server.class)
				&& KubernetesServer.class.isAssignableFrom(parameterContext.getParameter().getType()))
				|| (parameterContext.getParameter().isAnnotationPresent(Client.class)
						&& KubernetesClient.class.isAssignableFrom(parameterContext.getParameter().getType()));
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		mockServer.before();
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		mockServer.after();
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		if (parameterContext.getParameter().isAnnotationPresent(Client.class)) {
			return mockServer.getClient();
		}
		else {
			return mockServer;
		}
	}

	/**
	 * Enables injection of kubernetes server to test.
	 */
	@Target({ ElementType.PARAMETER })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Server {

	}

	/**
	 * Enables injection of kubernetes client to test.
	 */
	@Target({ ElementType.PARAMETER })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Client {

	}

}
