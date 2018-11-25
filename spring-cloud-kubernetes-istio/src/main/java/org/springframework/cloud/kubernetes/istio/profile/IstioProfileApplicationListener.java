/*
 *     Copyright (C) 2016 to the original authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.cloud.kubernetes.istio.profile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.cloud.kubernetes.istio.utils.MeshUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

public class IstioProfileApplicationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

	private static final Log LOG = LogFactory.getLog(IstioProfileApplicationListener.class);

	private static final String ISTIO_PROFILE = "istio";
	private static final int OFFSET = 1;
	private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + OFFSET;
	private final MeshUtils utils;

	public IstioProfileApplicationListener(MeshUtils utils) {
		this.utils = utils;
	}

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		ConfigurableEnvironment environment = event.getEnvironment();
		addKubernetesProfile(environment);
	}

	void addKubernetesProfile(ConfigurableEnvironment environment) {
		if (utils.isIstioEnabled()) {
			if (hasIstioProfile(environment)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("'istio' already in list of active profiles");
				}
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Adding 'istio' to list of active profiles");
				}
				environment.addActiveProfile(ISTIO_PROFILE);
			}
		} else {
			if (LOG.isDebugEnabled()) {
				LOG.warn("Not running inside kubernetes with istio enabled. Skipping 'istio' profile activation.");
			}
		}
	}

	private boolean hasIstioProfile(Environment environment) {
		for (String activeProfile : environment.getActiveProfiles()) {
			if (ISTIO_PROFILE.equalsIgnoreCase(activeProfile)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int getOrder() {
		return ORDER;
	}
}
