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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.SecretsPropertySource;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * A change detector that subscribes to changes in secrets and configmaps and fire a reload when something changes.
 */
public class EventBasedConfigurationChangeDetector extends ConfigurationChangeDetector {

    private ConfigMapPropertySourceLocator configMapPropertySourceLocator;

    private SecretsPropertySourceLocator secretsPropertySourceLocator;

    private Map<String, Watch> watches;

    public EventBasedConfigurationChangeDetector(AbstractEnvironment environment,
                                                 ConfigReloadProperties properties,
                                                 KubernetesClient kubernetesClient,
                                                 ConfigurationUpdateStrategy strategy,
                                                 ConfigMapPropertySourceLocator configMapPropertySourceLocator,
                                                 SecretsPropertySourceLocator secretsPropertySourceLocator) {
        super(environment, properties, kubernetesClient, strategy);

        this.configMapPropertySourceLocator = configMapPropertySourceLocator;
        this.secretsPropertySourceLocator = secretsPropertySourceLocator;
        this.watches = new HashMap<>();
    }

    @PostConstruct
    public void watch() {
        boolean activated = false;

        if (properties.isMonitoringConfigMaps()) {
            try {
                String name = "config-maps-watch";
                watches.put(name, kubernetesClient.configMaps()
                        .watch(new Watcher<ConfigMap>() {
                            @Override
                            public void eventReceived(Action action, ConfigMap configMap) {
                                onEvent(configMap);
                            }

                            @Override
                            public void onClose(KubernetesClientException e) {
                            }
                        }));
                activated = true;
                log.info("Added new Kubernetes watch: "+name);
            } catch (Exception e) {
                log.error("Error while establishing a connection to watch config maps: configuration may remain stale", e);
            }
        }

        if (properties.isMonitoringSecrets()) {
            try {
                activated = false;
                String name = "secrets-watch";
                watches.put(name, kubernetesClient.secrets()
                        .watch(new Watcher<Secret>() {
                            @Override
                            public void eventReceived(Action action, Secret secret) {
                                onEvent(secret);
                            }

                            @Override
                            public void onClose(KubernetesClientException e) {
                            }
                        }));
                activated = true;
                log.info("Added new Kubernetes watch: " + name);
            } catch (Exception e) {
                log.error("Error while establishing a connection to watch secrets: configuration may remain stale", e);
            }
        }

        if (activated) {
            log.info("Kubernetes event-based configuration change detector activated");
        }
    }

    @PreDestroy
    public void unwatch() {
        if (this.watches != null) {
            for (Map.Entry<String, Watch> entry : this.watches.entrySet()) {
                try {
                    log.debug("Closing the watch "+ entry.getKey());
                    entry.getValue().close();

                } catch (Exception e) {
                    log.error("Error while closing the watch connection", e);
                }
            }
        }
    }

    private void onEvent(ConfigMap configMap) {
		boolean changed = changed(
			locateMapPropertySources(configMapPropertySourceLocator, environment),
			findPropertySources(ConfigMapPropertySource.class)
		);
		if(changed) {
			log.info("Detected change in config maps");
			reloadProperties();
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
