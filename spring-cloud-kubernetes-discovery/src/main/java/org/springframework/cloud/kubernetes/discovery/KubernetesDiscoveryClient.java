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

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Utils;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class KubernetesDiscoveryClient implements DiscoveryClient {

    private static final String HOSTNAME = "HOSTNAME";

    private KubernetesClient client;
    private KubernetesDiscoveryProperties properties;

    public KubernetesDiscoveryClient(KubernetesClient client, KubernetesDiscoveryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public KubernetesClient getClient() {
        return client;
    }

    public void setClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public String description() {
        return "Kubernetes Discovery Client";
    }

    public ServiceInstance getLocalServiceInstance() {
        String serviceName = properties.getServiceName();
        String podName = System.getenv(HOSTNAME);
        ServiceInstance defaultInstance = new DefaultServiceInstance(serviceName, "localhost", 8080, false);

        Endpoints endpoints = client.endpoints().withName(serviceName).get();
        if (Utils.isNullOrEmpty(podName) || endpoints == null) {
            return defaultInstance;
        }
        try {
            return endpoints.getSubsets()
                    .stream()
                    .filter(s -> s.getAddresses().get(0).getTargetRef().getName().equals(podName))
                    .map(s -> (ServiceInstance) new KubernetesServiceInstance(serviceName,
                            s.getAddresses().stream().findFirst().orElseThrow(IllegalStateException::new),
                            s.getPorts().stream().findFirst().orElseThrow(IllegalStateException::new),
                            false))
                    .findFirst().orElse(defaultInstance);
        } catch (Throwable t) {
            return defaultInstance;
        }
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        Assert.notNull(serviceId, "[Assertion failed] - the object argument must be null");
        return Optional.ofNullable(client.endpoints().withName(serviceId).get()).orElse(new Endpoints())
                .getSubsets()
                .stream()
                .flatMap(s -> s.getAddresses().stream().map(a -> (ServiceInstance) new KubernetesServiceInstance(serviceId, a ,s.getPorts().stream().findFirst().orElseThrow(IllegalStateException::new), false)))
                .collect(Collectors.toList());

    }

    @Override
    public List<String> getServices() {
        return client.services().list()
                .getItems()
                .stream().map(s -> s.getMetadata().getName())
                .collect(Collectors.toList());
    }
}
