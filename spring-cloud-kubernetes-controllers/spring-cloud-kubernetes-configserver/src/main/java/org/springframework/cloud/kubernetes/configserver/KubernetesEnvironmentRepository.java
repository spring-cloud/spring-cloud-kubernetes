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

package org.springframework.cloud.kubernetes.configserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.StringUtils;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toCollection;

/**
 * @author Ryan Baxter
 */
public class KubernetesEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final String DEFAULT_PROFILE = "default";

	private static final LogAccessor LOG = new LogAccessor(KubernetesEnvironmentRepository.class);

	private final CoreV1Api coreApi;

	private final List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSuppliers;

	private final String namespace;

	private final int order;

	public KubernetesEnvironmentRepository(CoreV1Api coreApi,
			List<KubernetesPropertySourceSupplier> kubernetesPropertySourceSuppliers, String namespace,
			KubernetesConfigServerProperties properties) {
		this.coreApi = coreApi;
		this.kubernetesPropertySourceSuppliers = kubernetesPropertySourceSuppliers;
		this.namespace = namespace;
		this.order = properties.getOrder();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		return findOne(application, profile, label, true);
	}

	@Override
	public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		String[] profiles = profiles(profile);
		Environment environment = new Environment(application, profiles, label, null, null);
		LOG.debug(() -> "Profiles: " + profile + ", application: " + application + ", label: " + label);

		if (!"application".equalsIgnoreCase(application)) {
			addConfigurationsFromActiveProfiles(environment, application, profiles);
		}

		addConfigurationFromApplication(environment);
		return environment;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	private MutablePropertySources propertySources(String application) {
		Map<String, Object> applicationProperties = Map.of("spring.application.name", application);
		MapPropertySource propertySource = new MapPropertySource("kubernetes-config-server", applicationProperties);
		MutablePropertySources mutablePropertySources = new MutablePropertySources();
		mutablePropertySources.addFirst(propertySource);
		return mutablePropertySources;
	}

	private void addConfigurationFromApplication(Environment environment) {
		StandardEnvironment springEnv = new KubernetesConfigServerEnvironment(propertySources("application"));
		addApplicationConfiguration(environment, springEnv, "application");
	}

	private void addConfigurationsFromActiveProfiles(Environment environment, String applicationName,
			String[] profiles) {
		for (String activeProfile : profiles) {
			try {
				StandardEnvironment springEnv = new KubernetesConfigServerEnvironment(propertySources(applicationName));
				springEnv.setActiveProfiles(activeProfile);
				addApplicationConfiguration(environment, springEnv, applicationName);
			}
			catch (Exception e) {
				LOG.warn(e, () -> "Error while trying to find environment for application: " + applicationName
						+ " and profile : " + activeProfile);
			}
		}
	}

	private void addApplicationConfiguration(Environment environment, StandardEnvironment springEnv,
			String applicationName) {
		kubernetesPropertySourceSuppliers.forEach(supplier -> {
			List<MapPropertySource> propertySources = supplier.get(coreApi, applicationName, namespace, springEnv);
			propertySources.forEach(propertySource -> {
				LOG.debug(() -> "Adding PropertySource " + propertySource.getName());
				LOG.debug(() -> "PropertySource Names: " + Arrays.toString(propertySource.getPropertyNames()));
				environment.add(new PropertySource(propertySource.getName(), propertySource.getSource()));
			});
		});
	}

	/**
	 * Parses the requested profile string into the ordered array of profiles used when
	 * resolving configuration property sources.
	 * <ul>
	 * <li>Blank input falls back to {@value #DEFAULT_PROFILE}.</li>
	 * <li>Profile names are trimmed.</li>
	 * <li>Empty entries are ignored.</li>
	 * <li>Duplicates are removed while preserving request order.</li>
	 * <li>The resulting array is reversed so that profiles declared later in the request
	 * have higher precedence.</li>
	 * <li>{@value #DEFAULT_PROFILE} is always appended as the final fallback when not
	 * already present.</li>
	 * </ul>
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>{@code "dev,prod"} becomes {@code ["prod", "dev", "default"]}</li>
	 * <li>{@code "dev, prod, dev"} becomes {@code ["prod", "dev", "default"]}</li>
	 * <li>blank input becomes {@code ["default"]}</li>
	 * </ul>
	 * @param profile a comma-delimited profile string from the incoming request
	 * @return the ordered profiles to use when building property sources
	 */
	private String[] profiles(String profile) {

		if (!StringUtils.hasText(profile)) {
			profile = DEFAULT_PROFILE;
		}

		List<String> profiles = stream(profile.split(",")).map(String::trim)
			.filter(StringUtils::hasText)
			.distinct()
			.collect(toCollection(ArrayList::new));

		Collections.reverse(profiles);
		if (!profiles.contains(DEFAULT_PROFILE)) {
			profiles.add(DEFAULT_PROFILE);
		}

		return profiles.toArray(String[]::new);
	}

}
