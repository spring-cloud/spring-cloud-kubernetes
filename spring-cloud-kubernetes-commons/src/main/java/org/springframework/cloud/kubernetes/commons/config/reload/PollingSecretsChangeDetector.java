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

package org.springframework.cloud.kubernetes.commons.config.reload;

import java.time.Duration;

import jakarta.annotation.PostConstruct;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.log.LogAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * A change detector that periodically retrieves secrets and fires a reload when something
 * changes.
 *
 * @author Nicola Ferraro
 * @author Haytham Mohamed
 * @author Kris Iyer
 */
public class PollingSecretsChangeDetector extends ConfigurationChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(PollingSecretsChangeDetector.class);

	private final PropertySourceLocator propertySourceLocator;

	private final Class<? extends MapPropertySource> propertySourceClass;

	private final TaskScheduler taskExecutor;

	private final long period;

	private final boolean monitorSecrets;

	public PollingSecretsChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy, Class<? extends MapPropertySource> propertySourceClass,
			PropertySourceLocator propertySourceLocator, TaskScheduler taskExecutor) {
		super(environment, properties, strategy);
		this.propertySourceLocator = propertySourceLocator;
		this.propertySourceClass = propertySourceClass;
		this.taskExecutor = taskExecutor;
		this.period = properties.period().toMillis();
		this.monitorSecrets = properties.monitoringSecrets();
	}

	@PostConstruct
	private void init() {
		if (monitorSecrets) {
			LOG.info(() -> "Kubernetes polling secrets change detector activated");
			PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofMillis(period));
			trigger.setInitialDelay(Duration.ofMillis(period));
			taskExecutor.schedule(this::executeCycle, trigger);
		}
		else {
			LOG.info(() -> "Kubernetes polling secrets change detector disabled");
		}
	}

	private void executeCycle() {
		boolean changedSecrets = ConfigReloadUtil.reload(propertySourceLocator, environment, propertySourceClass);
		if (changedSecrets) {
			LOG.info(() -> "Detected change in secrets");
			reloadProperties();
		}
	}

}
