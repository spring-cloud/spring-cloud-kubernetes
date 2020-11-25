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

package org.springframework.cloud.kubernetes.istio;

import java.util.Arrays;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.istio.utils.MeshUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Auto configuration for Istio bootstrap.
 *
 * @author Mauricio Salatino
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.cloud.istio.enabled", matchIfMissing = true)
@EnableConfigurationProperties(IstioClientProperties.class)
public class IstioBootstrapConfiguration {

	private static final Log LOG = LogFactory.getLog(IstioBootstrapConfiguration.class);

	private static final String ISTIO_PROFILE = "istio";

	@Bean
	@ConditionalOnMissingBean
	public MeshUtils istioMeshUtils(IstioClientProperties istioClientProperties) {
		return new MeshUtils(istioClientProperties);
	}

	@EnableConfigurationProperties(IstioClientProperties.class)
	protected static class IstioDetectionConfiguration {

		private final MeshUtils utils;

		private final ConfigurableEnvironment environment;

		public IstioDetectionConfiguration(MeshUtils utils, ConfigurableEnvironment environment) {
			this.utils = utils;
			this.environment = environment;
		}

		@PostConstruct
		public void detectIstio() {
			addIstioProfile(this.environment);
		}

		void addIstioProfile(ConfigurableEnvironment environment) {
			if (this.utils.isIstioEnabled()) {
				if (hasIstioProfile(environment)) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("'istio' already in list of active profiles");
					}
				}
				else {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Adding 'istio' to list of active profiles");
					}
					environment.addActiveProfile(ISTIO_PROFILE);
				}
			}
			else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Not running inside kubernetes with istio enabled. Skipping 'istio' profile activation.");
				}
			}
		}

		private boolean hasIstioProfile(Environment environment) {
			return Arrays.stream(environment.getActiveProfiles()).anyMatch(ISTIO_PROFILE::equalsIgnoreCase);
		}

	}

}
