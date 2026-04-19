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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.test.context.ContextConfiguration;

/**
 * @author wind57
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@ExtendWith(Fabric8K3sIntegrationTestExtension.class)
@ContextConfiguration(initializers = K3sContextInitializer.class)
public @interface K3sIntegrationTest {

	/**
	 * what namespaces to create during this test.
	 */
	String[] namespaces() default {};

	/**
	 * in what namespaces must busybox be deployed.
	 */
	String[] busyboxNamespaces() default {};

	/**
	 * in what namespaces must wiremock be deployed.
	 */
	String[] wiremockNamespaces() default {};

	/**
	 * what image must be present in k3s.
	 */
	String[] withImages() default {};

	/**
	 * in what namespaces to set-up RBAC.
	 */
	String[] rbacNamespaces() default {};

	/**
	 * deploy external-name-service.
	 */
	boolean deployExternalNameService() default false;

	/**
	 * deploy istio.
	 */
	boolean deployIstio() default false;

	/**
	 * deploy or not configuration watcher.
	 */
	boolean deployConfigurationWatcher() default false;

}
