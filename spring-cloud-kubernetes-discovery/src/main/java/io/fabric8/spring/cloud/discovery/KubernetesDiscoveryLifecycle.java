/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.fabric8.spring.cloud.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.cloud.client.discovery.AbstractDiscoveryLifecycle;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class KubernetesDiscoveryLifecycle extends AbstractDiscoveryLifecycle {

    private KubernetesClient client;
    private KubernetesDiscoveryProperties properties;

    private AtomicBoolean running = new AtomicBoolean(false);

    public KubernetesDiscoveryLifecycle(KubernetesClient client, KubernetesDiscoveryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!isEnabled()) {
            return;
        }
        if (running.compareAndSet(false, true)) {
            register();
            getContext().publishEvent(new InstanceRegisteredEvent<>(this,
                    getConfiguration()));
        }
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }


    @Override
    protected int getConfiguredPort() {
        return client.getMasterUrl().getPort();
    }

    @Override
    protected void setConfiguredPort(int port) {

    }

    @Override
    protected Object getConfiguration() {
        return properties;
    }

    @Override
    protected void register() {
    }

    @Override
    protected void deregister() {
    }

    @Override
    protected boolean isEnabled() {
        return properties.isEnabled();
    }
}
