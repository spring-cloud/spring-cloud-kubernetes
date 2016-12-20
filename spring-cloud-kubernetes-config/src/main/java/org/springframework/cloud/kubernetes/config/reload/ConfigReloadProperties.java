/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.springframework.cloud.kubernetes.config.reload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * General configuration for the configuration reload.
 */
@ConfigurationProperties(prefix = "spring.cloud.kubernetes.reload")
public class ConfigReloadProperties {

    /**
     * Enables the Kubernetes configuration reload on change.
     */
    private boolean enabled = false;

    /**
     * Enables monitoring on config maps to detect changes.
     */
    private boolean monitoringConfigMaps = true;

    /**
     * Enables monitoring on secrets to detect changes.
     */
    private boolean monitoringSecrets = false;

    /**
     * Sets the reload strategy for Kubernetes configuration reload on change.
     */
    private ReloadStrategy strategy = ReloadStrategy.REFRESH;

    /**
     * Sets the detection mode for Kubernetes configuration reload.
     */
    private ReloadDetectionMode mode = ReloadDetectionMode.EVENT;

    /**
     * Sets the polling period in milliseconds to use when the detection mode is POLLING.
     */
    private Long period = 15000L;

    public ConfigReloadProperties() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMonitoringConfigMaps() {
        return monitoringConfigMaps;
    }

    public void setMonitoringConfigMaps(boolean monitoringConfigMaps) {
        this.monitoringConfigMaps = monitoringConfigMaps;
    }

    public boolean isMonitoringSecrets() {
        return monitoringSecrets;
    }

    public void setMonitoringSecrets(boolean monitoringSecrets) {
        this.monitoringSecrets = monitoringSecrets;
    }

    public ReloadStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(ReloadStrategy strategy) {
        this.strategy = strategy;
    }

    public ReloadDetectionMode getMode() {
        return mode;
    }

    public Long getPeriod() {
        return period;
    }

    public void setPeriod(Long period) {
        this.period = period;
    }

    public void setMode(ReloadDetectionMode mode) {
        this.mode = mode;
    }

    public enum ReloadStrategy {
        /**
         * Fire a refresh of beans annotated with @ConfigurationProperties or @RefreshScope.
         */
        REFRESH,

        /**
         * Restarts the Spring ApplicationContext to apply the new configuration.
         */
        RESTART_CONTEXT,

        /**
         * Shuts down the Spring ApplicationContext to activate a restart of the container.
         * Make sure that the lifecycle of all non-daemon threads is bound to the ApplicationContext and that
         * a replication controller or replica set is configured to restart the pod.
         */
        SHUTDOWN
    }

    public enum ReloadDetectionMode {
        /**
         * Enables a polling task that retrieves periodically all external properties and
         * fire a reload when they change.
         */
        POLLING,

        /**
         * Listens to Kubernetes events and checks if a reload is needed when configmaps or secrets change.
         */
        EVENT
    }

}
