/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config.reload;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * General configuration for the configuration reload.
 * @param enabled Enables the Kubernetes configuration reload on change.
 * @param monitoringConfigMaps Enables monitoring on secrets to detect changes.
 * @param monitoringSecrets Monitor secrets or not.
 * @param strategy Sets reload strategy for Kubernetes configuration reload on change.
 * @param mode Sets the detection mode for Kubernetes configuration reload.
 * @param period Sets the polling period to use when the detection mode is POLLING.
 * @param namespaces namespaces where an informer will be set-up. this property is only
 * relevant for event based reloading.
 * @param enableReloadFiltering create an informer only for sources that have
 * 'spring.cloud.kubernetes.config.informer.enabled=true' label. This property is only
 * relevant for event based reloading.
 * @param maxWaitForRestart Restart or Shutdown strategies are used, Spring Cloud
 * Kubernetes waits a random amount of time before restarting. This is done in order to
 * avoid having all instances of the same application restart at the same time. This
 * property configures the maximum of amount of wait time from the moment the signal is
 * received that a restart is needed until the moment the restart is actually triggered
 *
 * @author Nicola Ferraro
 */
@ConfigurationProperties(prefix = "spring.cloud.kubernetes.reload")
public record ConfigReloadProperties(boolean enabled, @DefaultValue("true") boolean monitoringConfigMaps,
		boolean monitoringSecrets, @DefaultValue("REFRESH") ReloadStrategy strategy,
		@DefaultValue("EVENT") ReloadDetectionMode mode, @DefaultValue("15000ms") Duration period,
		@DefaultValue Set<String> namespaces, boolean enableReloadFiltering,
		@DefaultValue("2s") Duration maxWaitForRestart) {

	/**
	 * default instance.
	 */
	public static ConfigReloadProperties DEFAULT = new ConfigReloadProperties(false, true, false,
			ReloadStrategy.REFRESH, ReloadDetectionMode.EVENT, Duration.ofMillis(15000), Set.of(), false,
			Duration.ofSeconds(2));

	/**
	 * label for filtering sources.
	 */
	public static final String RELOAD_LABEL_FILTER = "spring.cloud.kubernetes.config.informer.enabled";

	/**
	 * Reload strategies.
	 */
	public enum ReloadStrategy {

		/**
		 * Fire a refresh of beans annotated with @ConfigurationProperties
		 * or @RefreshScope.
		 */
		REFRESH,

		/**
		 * Restarts the Spring ApplicationContext to apply the new configuration.
		 */
		RESTART_CONTEXT,

		/**
		 * Shuts down the Spring ApplicationContext to activate a restart of the
		 * container. Make sure that the lifecycle of all non-daemon threads is bound to
		 * the ApplicationContext and that a replication controller or replica set is
		 * configured to restart the pod.
		 */
		SHUTDOWN

	}

	/**
	 * Reload detection modes.
	 */
	public enum ReloadDetectionMode {

		/**
		 * Enables a polling task that retrieves periodically all external properties and
		 * fire a reload when they change.
		 */
		POLLING,

		/**
		 * Listens to Kubernetes events and checks if a reload is needed when configmaps
		 * or secrets change.
		 */
		EVENT

	}

}
