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

import java.util.HashMap;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public class ConfigMapEnvironmentRepository implements EnvironmentRepository {

	private static final Log LOG = LogFactory.getLog(ConfigMapEnvironmentRepository.class);

	private CoreV1Api coreApi;

	private String namespace;

	public ConfigMapEnvironmentRepository(CoreV1Api coreApi, String namespace) {
		this.coreApi = coreApi;
		this.namespace = namespace;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		return findOne(application, profile, label, true);
	}

	@Override
	public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		LOG.info("Profiles: " + profile);
		LOG.info("Application: " + application);
		LOG.info("Label: " + label);
		LOG.info("Namespace: " + namespace);
		Environment environment = new Environment(application, profiles, label, null, null);
		try {
			StandardEnvironment springEnv = new StandardEnvironment();
			springEnv.setActiveProfiles(profiles);
			KubernetesClientConfigMapPropertySource applicationConfigMapPropertySource = new KubernetesClientConfigMapPropertySource(
					coreApi, "application", namespace, springEnv, "");
			KubernetesClientSecretsPropertySource applicationSecretsPropertySource = new KubernetesClientSecretsPropertySource(
					coreApi, "application", namespace, springEnv, new HashMap<>());
			if (applicationConfigMapPropertySource.getPropertyNames().length > 0) {
				LOG.info("Adding PropertySource " + applicationConfigMapPropertySource.getName());
				LOG.info("PropertySource Names: " + applicationConfigMapPropertySource.getPropertyNames());
				environment.add(new PropertySource(applicationConfigMapPropertySource.getName(),
						applicationConfigMapPropertySource.getSource()));
			}
			if (applicationSecretsPropertySource.getPropertyNames().length > 0) {
				LOG.info("Adding PropertySource " + applicationSecretsPropertySource.getName());
				LOG.info("PropertySource Names: " + applicationSecretsPropertySource.getPropertyNames());
				environment.add(new PropertySource(applicationSecretsPropertySource.getName(),
						applicationSecretsPropertySource.getSource()));
			}
			if (!"application".equalsIgnoreCase(application)) {
				KubernetesClientConfigMapPropertySource configMapPropertySource = new KubernetesClientConfigMapPropertySource(
						coreApi, application, namespace, springEnv, "");
				KubernetesClientSecretsPropertySource secretsPropertySource = new KubernetesClientSecretsPropertySource(
						coreApi, application, namespace, springEnv, new HashMap<>());
				if (configMapPropertySource.getPropertyNames().length > 0) {
					LOG.info("Adding PropertySource " + configMapPropertySource.getName());
					LOG.info("PropertySource Names: " + configMapPropertySource.getPropertyNames());
					environment.add(
							new PropertySource(configMapPropertySource.getName(), configMapPropertySource.getSource()));
				}
				if (secretsPropertySource.getPropertyNames().length > 0) {
					LOG.info("Adding PropertySource " + secretsPropertySource.getName());
					LOG.info("PropertySource Names: " + secretsPropertySource.getPropertyNames());
					environment.add(
							new PropertySource(secretsPropertySource.getName(), secretsPropertySource.getSource()));
				}

			}

			return environment;
		}
		catch (Exception e) {
			LOG.warn(e);
		}
		return environment;
	}

}
