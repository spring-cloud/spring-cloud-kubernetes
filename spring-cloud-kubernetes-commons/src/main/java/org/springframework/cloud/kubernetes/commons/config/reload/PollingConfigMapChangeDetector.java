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
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

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

	private PropertySourceLocator propertySourceLocator;

	private Class propertySourceClass;

	private TaskScheduler taskExecutor;

	private Duration period = Duration.ofMillis(1500);

	@Deprecated
	public PollingConfigMapChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy, Class propertySourceClass,
			PropertySourceLocator propertySourceLocator) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.propertySourceClass = propertySourceClass;
		this.taskExecutor = new TaskSchedulerBuilder().build();
	}

	public PollingConfigMapChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy, Class propertySourceClass,
			PropertySourceLocator propertySourceLocator, TaskScheduler taskExecutor) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.propertySourceClass = propertySourceClass;
		this.taskExecutor = taskExecutor;
		this.period = properties.getPeriod();
	}

	@PostConstruct
	public void init() {
		this.log.info("Kubernetes polling configMap change detector activated");
		PeriodicTrigger trigger = new PeriodicTrigger(period.toMillis());
		trigger.setInitialDelay(period.toMillis());
		taskExecutor.schedule(this::executeCycle, trigger);
	}

	public void executeCycle() {

		boolean changedConfigMap = false;
		if (this.properties.isMonitoringConfigMaps()) {
			if (log.isDebugEnabled()) {
				log.debug("Polling for changes in config maps");
			}
			List<? extends MapPropertySource> currentConfigMapSources = findPropertySources(propertySourceClass);

			if (!currentConfigMapSources.isEmpty()) {
				changedConfigMap = changed(locateMapPropertySources(this.propertySourceLocator, this.environment),
						currentConfigMapSources);
			}
		}

		if (changedConfigMap) {
			this.log.info("Detected change in config maps");
			reloadProperties();
		}
	}

}
