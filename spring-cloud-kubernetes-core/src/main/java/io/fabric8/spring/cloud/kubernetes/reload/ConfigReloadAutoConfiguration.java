package io.fabric8.spring.cloud.kubernetes.reload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.PreDestroy;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.spring.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import io.fabric8.spring.cloud.kubernetes.config.SecretsPropertySourceLocator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

/**
 * Definition of beans needed for the automatic reload of configuration.
 */
@Configuration
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
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
        public ConfigurationChangeDetector propertyChangeWatcher(ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy, EventWatcher eventWatcher) {
            switch (properties.getMode()) {
            case POLLING:
                return new PollingConfigurationChangeDetector(environment, properties, kubernetesClient, strategy, configMapPropertySourceLocator, secretsPropertySourceLocator);
            case EVENT:
                return new EventBasedConfigurationChangeDetector(environment, properties, kubernetesClient, strategy, configMapPropertySourceLocator, secretsPropertySourceLocator, eventWatcher);
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


        /**
         * Manages watches asynchronously and clean them up on context close.
         */
        @Component
        public static class DefaultEventWatcher implements EventWatcher {
            private Logger log = LoggerFactory.getLogger(getClass());

            private KubernetesClient kubernetesClient;

            private Map<String, Watch> watches;

            @Autowired
            public DefaultEventWatcher(KubernetesClient kubernetesClient) {
                this.kubernetesClient = kubernetesClient;
                this.watches = new ConcurrentHashMap<>();
            }

            @Async
            public void addWatch(String name, Function<KubernetesClient, Watch> watch) {
                if (watches.containsKey(name)) {
                    throw new IllegalArgumentException("Watch already present: " + name);
                }

                watches.put(name, watch.apply(kubernetesClient));
                log.info("Added new Kubernetes watch: {}", name);
            }

            @PreDestroy
            public void unwatch() {
                if (this.watches != null) {
                    for (Watch watch : this.watches.values()) {
                        try {
                            watch.close();

                        } catch (Exception e) {
                            log.error("Error while closing the watch connection", e);
                        }
                    }
                }
            }

        }
    }

}
