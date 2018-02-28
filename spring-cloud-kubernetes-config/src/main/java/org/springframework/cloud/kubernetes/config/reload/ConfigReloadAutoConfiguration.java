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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Definition of beans needed for the automatic reload of configuration.
 */
@Configuration
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@AutoConfigureAfter({InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class, RefreshAutoConfiguration.class})
@EnableConfigurationProperties(ConfigReloadProperties.class)

public class ConfigReloadAutoConfiguration {

    /**
     * Configuration reload must be enabled explicitly.
     */
    @ConditionalOnProperty(value = "spring.cloud.kubernetes.reload.enabled")
    @ConditionalOnClass({RestartEndpoint.class, ContextRefresher.class})
    @EnableScheduling
    @EnableAsync
    protected static class ConfigReloadAutoConfigurationBeans {

        @Autowired
        private AbstractEnvironment environment;

        @Autowired
        private KubernetesClient kubernetesClient;

        @Autowired
        private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

        @Autowired
        private SecretsPropertySourceLocator secretsPropertySourceLocator;

        /**
         * Provides a bean that listen to configuration changes and fire a reload.
         */
        @Bean
        @ConditionalOnMissingBean
        public ConfigurationChangeDetector propertyChangeWatcher(ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy) {
            switch (properties.getMode()) {
            case POLLING:
                return new PollingConfigurationChangeDetector(environment, properties, kubernetesClient, strategy, configMapPropertySourceLocator, secretsPropertySourceLocator);
            case EVENT:
                return new EventBasedConfigurationChangeDetector(environment, properties, kubernetesClient, strategy, configMapPropertySourceLocator, secretsPropertySourceLocator);
            }
            throw new IllegalStateException("Unsupported configuration reload mode: " + properties.getMode());
        }

        /**
         * Provides the action to execute when the configuration changes.
         */
        @Bean
        @ConditionalOnMissingBean
        public ConfigurationUpdateStrategy configurationUpdateStrategy(ConfigReloadProperties properties, ConfigurableApplicationContext ctx, RestartEndpoint restarter, ContextRefresher refresher) {
            switch (properties.getStrategy()) {
            case RESTART_CONTEXT:
                return new ConfigurationUpdateStrategy(properties.getStrategy().name(), restarter::restart);
            case REFRESH:
                return new ConfigurationUpdateStrategy(properties.getStrategy().name(), refresher::refresh);
            case SHUTDOWN:
                return new ConfigurationUpdateStrategy(properties.getStrategy().name(), ctx::close);
            }
            throw new IllegalStateException("Unsupported configuration update strategy: " + properties.getStrategy());
        }

    }

}
