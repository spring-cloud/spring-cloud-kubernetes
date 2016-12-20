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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.PreDestroy;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * This is the superclass of all beans that can listen to changes in the configuration and fire a reload.
 */
public abstract class ConfigurationChangeDetector {

    protected Logger log = LoggerFactory.getLogger(getClass());

    protected ConfigurableEnvironment environment;

    protected ConfigReloadProperties properties;

    protected KubernetesClient kubernetesClient;

    protected ConfigurationUpdateStrategy strategy;

    public ConfigurationChangeDetector(ConfigurableEnvironment environment, ConfigReloadProperties properties, KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy) {
        this.environment = environment;
        this.properties = properties;
        this.kubernetesClient = kubernetesClient;
        this.strategy = strategy;
    }

    @PreDestroy
    public void shutdown() {
        // Ensure the kubernetes client is cleaned up from spare threads when shutting down
        kubernetesClient.close();
    }

    public void reloadProperties() {
        log.info("Reloading using strategy: " + strategy.getName());
        strategy.reload();
    }

    /**
     * Determines if two property sources are different.
     */
    protected boolean changed(MapPropertySource mp1, MapPropertySource mp2) {
        if (mp1 == mp2) return false;
        if (mp1 == null && mp2 != null || mp1 != null && mp2 == null) return true;

        Map<String, Object> s1 = mp1.getSource();
        Map<String, Object> s2 = mp2.getSource();

        return s1 == null ? s2 != null : !s1.equals(s2);
    }

    /**
     * Finds one registered property source of the given type, logging a warning if
     * multiple property sources of that type are available.
     */
    protected <S extends PropertySource<?>> S findPropertySource(Class<S> sourceClass) {
        List<S> sources = findPropertySources(sourceClass);
        if (sources.size() == 0) {
            return null;
        }
        if (sources.size() > 1) {
            log.warn("Found more than one property source of type " + sourceClass);
        }
        return sources.get(0);
    }

    /**
     * Finds all registered property sources of the given type.
     */
    protected <S extends PropertySource<?>> List<S> findPropertySources(Class<S> sourceClass) {
        List<S> managedSources = new LinkedList<>();

        LinkedList<PropertySource<?>> sources = toLinkedList(environment.getPropertySources());
        while (!sources.isEmpty()) {
            PropertySource<?> source = sources.pop();
            if (source instanceof CompositePropertySource) {
                CompositePropertySource comp = (CompositePropertySource) source;
                sources.addAll(comp.getPropertySources());
            } else if (sourceClass.isInstance(source)) {
                managedSources.add(sourceClass.cast(source));
            }
        }

        return managedSources;
    }

    private <E> LinkedList<E> toLinkedList(Iterable<E> it) {
        LinkedList<E> list = new LinkedList<E>();
        for (E e : it) {
            list.add(e);
        }
        return list;
    }

}
