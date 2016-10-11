package io.fabric8.spring.cloud.kubernetes.reload;

import javax.annotation.PostConstruct;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.spring.cloud.kubernetes.config.ConfigMapPropertySource;
import io.fabric8.spring.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import io.fabric8.spring.cloud.kubernetes.config.SecretsPropertySource;
import io.fabric8.spring.cloud.kubernetes.config.SecretsPropertySourceLocator;

import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * A change detector that subscribes to changes in secrets and configmaps and fire a reload when something changes.
 */
public class EventBasedConfigurationChangeDetector extends ConfigurationChangeDetector {

    private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

    private SecretsPropertySourceLocator secretsPropertySourceLocator;

    private EventWatcher eventWatcher;

    public EventBasedConfigurationChangeDetector(AbstractEnvironment environment,
                                                 ConfigReloadProperties properties,
                                                 KubernetesClient kubernetesClient,
                                                 ConfigurationUpdateStrategy strategy,
                                                 ConfigMapPropertySourceLocator configMapPropertySourceLocator,
                                                 SecretsPropertySourceLocator secretsPropertySourceLocator,
                                                 EventWatcher eventWatcher) {
        super(environment, properties, kubernetesClient, strategy);

        this.eventWatcher = eventWatcher;
        this.configMapPropertySourceLocator = configMapPropertySourceLocator;
        this.secretsPropertySourceLocator = secretsPropertySourceLocator;
    }

    @PostConstruct
    public void watch() {

        if (properties.isMonitoringConfigMaps()) {
            eventWatcher.addWatch("config-maps-watch", k -> k.configMaps()
                    .watch(new Watcher<ConfigMap>() {
                        @Override
                        public void eventReceived(Action action, ConfigMap configMap) {
                            onEvent(configMap);
                        }

                        @Override
                        public void onClose(KubernetesClientException e) {
                        }
                    }));
        }

        if (properties.isMonitoringSecrets()) {
            eventWatcher.addWatch("secrets-watch", k -> k.secrets()
                    .watch(new Watcher<Secret>() {
                        @Override
                        public void eventReceived(Action action, Secret secret) {
                            onEvent(secret);
                        }

                        @Override
                        public void onClose(KubernetesClientException e) {
                        }
                    }));
        }

        log.info("Kubernetes polling configuration change detector activated");
    }

    private void onEvent(ConfigMap configMap) {
        MapPropertySource currentConfigMapSource = findPropertySource(ConfigMapPropertySource.class);
        if (currentConfigMapSource != null) {
            MapPropertySource newConfigMapSource = configMapPropertySourceLocator.locate(environment);
            if (changed(currentConfigMapSource, newConfigMapSource)) {
                log.info("Detected change in config maps");
                reloadProperties();
            }
        }
    }

    private void onEvent(Secret secret) {
        MapPropertySource currentSecretSource = findPropertySource(SecretsPropertySource.class);
        if (currentSecretSource != null) {
            MapPropertySource newSecretSource = secretsPropertySourceLocator.locate(environment);
            if (changed(currentSecretSource, newSecretSource)) {
                log.info("Detected change in secrets");
                reloadProperties();
            }
        }
    }


}
