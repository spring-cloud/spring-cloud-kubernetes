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
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.stream.Collectors;

public class KubernetesDiscoveryClient implements DiscoveryClient {

    private static final String HOSTNAME = "HOSTNAME";

    private KubernetesClient client;
    private String localServiceId;

    public KubernetesDiscoveryClient(KubernetesClient client, String localServiceId) {
        this.client = client;
        this.localServiceId = localServiceId;
    }

    public KubernetesClient getClient() {
        return client;
    }

    public void setClient(KubernetesClient client) {
        this.client = client;
    }

    public String getLocalServiceId() {
        return localServiceId;
    }

    public void setLocalServiceId(String localServiceId) {
        this.localServiceId = localServiceId;
    }

    @Override
    public String description() {
        return "Kubernetes Discovery Client";
    }

    @Override
    public ServiceInstance getLocalServiceInstance() {
        String podName = System.getenv(HOSTNAME);
        return client.endpoints().withName(localServiceId).get().getSubsets()
                .stream()
                .filter(s -> s.getAddresses().iterator().next().getIp().equals(podName))
                .map(s -> new KubernetesServiceInstance(localServiceId, s.getPorts().iterator().next().getName(), s, false))
                .findFirst().get();
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return client.endpoints().withName(serviceId).get()
                .getSubsets()
                .stream().map(s -> new KubernetesServiceInstance(serviceId, s.getPorts().iterator().next().getName(), s, false))
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
