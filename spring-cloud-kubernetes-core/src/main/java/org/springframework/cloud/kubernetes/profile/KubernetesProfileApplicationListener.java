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

package org.springframework.cloud.kubernetes.profile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

public class KubernetesProfileApplicationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Log LOG = LogFactory.getLog(KubernetesProfileApplicationListener.class);

    private static final String KUBERNETES_PROFILE = "kubernetes";
    private static final int OFFSET = 1;
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + OFFSET;
    private final PodUtils utils;

    public KubernetesProfileApplicationListener(PodUtils utils) {
        this.utils = utils;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        addKubernetesProfile(environment);
    }

    void addKubernetesProfile(ConfigurableEnvironment environment) {
        if (utils.isInsideKubernetes()) {
            if (hasKubernetesProfile(environment)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("'kubernetes' already in list of active profiles");
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adding 'kubernetes' to list of active profiles");
                }
                environment.addActiveProfile(KUBERNETES_PROFILE);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.warn("Not running inside kubernetes. Skipping 'kubernetes' profile activation.");
            }
        }
    }

    private boolean hasKubernetesProfile(Environment environment) {
        for (String activeProfile : environment.getActiveProfiles()) {
            if (KUBERNETES_PROFILE.equalsIgnoreCase(activeProfile)) {
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
