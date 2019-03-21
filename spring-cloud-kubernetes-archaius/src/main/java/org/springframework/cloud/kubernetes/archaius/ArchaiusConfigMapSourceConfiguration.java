/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.archaius;

import com.netflix.config.WatchedConfigurationSource;
import com.netflix.config.WatchedUpdateListener;
import com.netflix.config.WatchedUpdateResult;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ArchaiusConfigMapSourceConfiguration implements InitializingBean, DisposableBean, WatchedConfigurationSource, Closeable {

    private final KubernetesClient client;
    private final String name;
    private final String namespace;
    private final List<WatchedUpdateListener> listeners = new ArrayList<>();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AtomicReference<Map<String, Object>> currentData = new AtomicReference<>();
    private Watch watch;

    private volatile Watcher<ConfigMap> watcher = new Watcher<ConfigMap>() {
        @Override
        public void eventReceived(Action action, ConfigMap configMap) {
            offer(WatchedUpdateResult.createFull(asObjectMap(configMap.getData())));
        }

        @Override
        public void onClose(KubernetesClientException e) {

        }
    };

    public ArchaiusConfigMapSourceConfiguration(KubernetesClient client, String name, String namespace) {
        this.client = client;
        this.name = name;
        this.namespace = namespace;
    }


    public void start() {
        ConfigMap map = StringUtils.isEmpty(namespace)
                ? client.configMaps().withName(name).get()
                : client.configMaps().inNamespace(namespace).withName(name).get();

        if (map != null) {
            currentData.set(asObjectMap(map.getData()));
        }
        watch = StringUtils.isEmpty(namespace)
                ? client.configMaps().withName(name).watch(watcher)
                : client.configMaps().inNamespace(namespace).withName(name).watch(watcher);
        started.set(true);
    }

    @Override
    public void close() throws IOException {
        started.set(false);
        if (watch != null) {
            watch.close();
        }
        executorService.shutdown();
    }

    @Override
    public synchronized void addUpdateListener(WatchedUpdateListener watchedUpdateListener) {
        listeners.add(watchedUpdateListener);
    }

    @Override
    public synchronized void removeUpdateListener(WatchedUpdateListener watchedUpdateListener) {
        listeners.remove(watchedUpdateListener);
    }

    @Override
    public Map<String, Object> getCurrentData() throws Exception {
        return currentData.get();
    }

    private void offer(WatchedUpdateResult event) {
        submit(() -> {
            listeners.stream().forEach(l -> l.updateConfiguration(event));
            currentData.set(event.getComplete());
        });
    }

    private synchronized void submit(final Runnable command) {
        if (started.get()) {
            executorService.submit(command);
        }
    }

    private static Map<String, Object> asObjectMap(Map<String, String> source) {
        return source.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void destroy() throws Exception {
        close();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }
}
