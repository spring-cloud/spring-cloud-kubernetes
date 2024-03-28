/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.configserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public class KubernetesEnvironmentRepository implements EnvironmentRepository {

	private static final Log LOG = LogFactory.getLog(KubernetesEnvironmentRepository.class);

	private final CoreV1Api coreApi;

	private final List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSuppliers;

	private final String namespace;

	public KubernetesEnvironmentRepository(CoreV1Api coreApi,
			List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSuppliers, String namespace) {
		this.coreApi = coreApi;
		this.kubernetesPropertySourceSuppliers = kubernetesPropertySourceSuppliers;
		this.namespace = namespace;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		return findOne(application, profile, label, true);
	}

	@Override
	public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		if (!StringUtils.hasText(profile)) {
			profile = "default";
		}
		List<String> profiles = new ArrayList<>(List.of(StringUtils.commaDelimitedListToStringArray(profile)));

		Collections.reverse(profiles);
		if (!profiles.contains("default")) {
			profiles.add("default");
		}
		Environment environment = new Environment(application, profiles.toArray(profiles.toArray(new String[0])), label,
				null, null);
		LOG.info("Profiles: " + profile);
		LOG.info("Application: " + application);
		LOG.info("Label: " + label);
		for (String activeProfile : profiles) {
			try {
				// This is needed so that when we get the application name in
				// SourceDataProcessor.sorted that it actually
				// exists in the Environment
				StandardEnvironment springEnv = new KubernetesConfigServerEnvironment(
						createPropertySources(application));
				springEnv.setActiveProfiles(activeProfile);
				if (!"application".equalsIgnoreCase(application)) {
					addApplicationConfiguration(environment, springEnv, application);
				}
			}
			catch (Exception e) {
				LOG.warn(e);
			}
		}
		StandardEnvironment springEnv = new KubernetesConfigServerEnvironment(createPropertySources("application"));
		addApplicationConfiguration(environment, springEnv, "application");
		return environment;
	}

	private MutablePropertySources createPropertySources(String application) {
		Map<String, Object> applicationProperties = Map.of("spring.application.name", application);
		MapPropertySource propertySource = new MapPropertySource("kubernetes-config-server", applicationProperties);
		MutablePropertySources mutablePropertySources = new MutablePropertySources();
		mutablePropertySources.addFirst(propertySource);
		return mutablePropertySources;
	}

	private void addApplicationConfiguration(Environment environment, StandardEnvironment springEnv,
			String applicationName) {
		kubernetesPropertySourceSuppliers.forEach(supplier -> {
			List<MapPropertySource> propertySources = supplier.get(coreApi, applicationName, namespace, springEnv);
			propertySources.forEach(propertySource -> {
				if (propertySource.getPropertyNames().length > 0) {
					LOG.debug("Adding PropertySource " + propertySource.getName());
					LOG.debug("PropertySource Names: "
							+ StringUtils.arrayToCommaDelimitedString(propertySource.getPropertyNames()));
					environment.add(new PropertySource(propertySource.getName(), propertySource.getSource()));
				}
			});
		});
	}

}
