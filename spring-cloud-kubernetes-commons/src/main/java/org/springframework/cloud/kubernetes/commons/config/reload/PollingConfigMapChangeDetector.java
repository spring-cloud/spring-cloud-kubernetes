/*
 * Copyright 2013-2022 the original author or authors.
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
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

import static org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil.changed;
import static org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil.findPropertySources;
import static org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadUtil.locateMapPropertySources;

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

	private final PropertySourceLocator propertySourceLocator;

	private final Class<? extends MapPropertySource> propertySourceClass;

	private final TaskScheduler taskExecutor;

	private final long period;

	private final boolean monitorConfigMaps;

	public PollingConfigMapChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy, Class<? extends MapPropertySource> propertySourceClass,
			PropertySourceLocator propertySourceLocator, TaskScheduler taskExecutor) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.propertySourceClass = propertySourceClass;
		this.taskExecutor = taskExecutor;
		this.period = properties.period().toMillis();
		this.monitorConfigMaps = properties.monitoringConfigMaps();
	}

	@PostConstruct
	private void init() {
		log.info("Kubernetes polling configMap change detector activated");
		PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofMillis(period));
		trigger.setInitialDelay(Duration.ofMillis(period));
		taskExecutor.schedule(this::executeCycle, trigger);
	}

	private void executeCycle() {

		boolean changedConfigMap = false;
		if (monitorConfigMaps) {
			log.debug("Polling for changes in config maps");
			List<? extends MapPropertySource> currentConfigMapSources = findPropertySources(propertySourceClass,
					environment);

			if (!currentConfigMapSources.isEmpty()) {
				changedConfigMap = changed(locateMapPropertySources(this.propertySourceLocator, this.environment),
						currentConfigMapSources);
			}
		}

		if (changedConfigMap) {
			log.info("Detected change in config maps");
			reloadProperties();
		}
	}

}
