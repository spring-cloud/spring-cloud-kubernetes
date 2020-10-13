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

package org.springframework.cloud.kubernetes.config.reload;

import java.util.List;

import javax.annotation.PostConstruct;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A change detector that periodically retrieves configmaps and fire a reload when
 * something changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 * @author Kris Iyer
 */
public class PollingConfigMapChangeDetector extends ConfigurationChangeDetector {

	protected Log log = LogFactory.getLog(getClass());

	private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

	public PollingConfigMapChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			ConfigMapPropertySourceLocator configMapPropertySourceLocator) {
		super(environment, properties, kubernetesClient, strategy);

		this.configMapPropertySourceLocator = configMapPropertySourceLocator;
	}

	@PostConstruct
	public void init() {
		this.log.info("Kubernetes polling configMap change detector activated");
	}

	@Scheduled(initialDelayString = "${spring.cloud.kubernetes.reload.period:15000}",
			fixedDelayString = "${spring.cloud.kubernetes.reload.period:15000}")
	public void executeCycle() {

		boolean changedConfigMap = false;
		if (this.properties.isMonitoringConfigMaps()) {
			if (log.isDebugEnabled()) {
				log.debug("Polling for changes in config maps");
			}
			List<? extends MapPropertySource> currentConfigMapSources = findPropertySources(
					ConfigMapPropertySource.class);

			if (!currentConfigMapSources.isEmpty()) {
				changedConfigMap = changed(
						locateMapPropertySources(this.configMapPropertySourceLocator, this.environment),
						currentConfigMapSources);
			}
		}

		if (changedConfigMap) {
			this.log.info("Detected change in config maps");
			reloadProperties();
		}
	}

}
