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
@ContextConfiguration(initializers = K3sContextInitializer.class)
@ExtendWith(NativeClientIntegrationTestExtension.class)
public @interface NativeClientIntegrationTest {

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
	Wiremock wiremock() default @Wiremock(enabled = false);

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
	 * deploy discovery server.
	 */
	boolean deployDiscoverServer() default false;

	/**
	 * deploy kafka.
	 */
	boolean deployKafka() default false;

	/**
	 * deploy rabbitMq.
	 */
	boolean deployRabbitMq() default false;

	/**
	 * configuration watcher settings.
	 */
	ConfigurationWatcher configurationWatcher() default @ConfigurationWatcher(enabled = false);

	ClusterWideRBAC clusterWideRBAC() default @ClusterWideRBAC(enabled = false);

	@interface ConfigurationWatcher {

		/**
		 * is configuration watcher deployment enabled or not.
		 */
		boolean enabled() default false;

		/**
		 * 'SPRING_CLOUD_KUBERNETES_CONFIGURATION_WATCHER_REFRESHDELAY' value.
		 */
		String refreshDelay() default "120000";

		/**
		 * 'SPRING_CLOUD_KUBERNETES_RELOAD_ENABLED' value.
		 */
		boolean reloadEnabled() default true;

		/**
		 * what namespaces to be watched. 'SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_0'
		 * and so on
		 */
		String[] watchNamespaces() default {};

		/**
		 * use kafka as amqp-bus.
		 */
		boolean kafkaEnabled() default false;

		/**
		 * use rabbitmq as amqp-bus.
		 */
		boolean rabbitMqEnabled() default false;

	}

	@interface ClusterWideRBAC {

		/**
		 * is RBAC for cluster wide settings enabled.
		 */
		boolean enabled() default false;

		/**
		 * namespace of the service account.
		 */
		String serviceAccountNamespace() default "";

		/**
		 * role binding namespaces.
		 */
		String[] roleBindingNamespaces() default {};

	}

	@interface Wiremock {

		/**
		 * enable wiremock deployment or not.
		 */
		boolean enabled() default false;

		/**
		 * in what namespaces to deploy wiremock.
		 */
		String[] namespaces() default {};

		/**
		 * enable nodePort on the wiremockService.
		 */
		boolean withNodePort() default false;

	}

}
