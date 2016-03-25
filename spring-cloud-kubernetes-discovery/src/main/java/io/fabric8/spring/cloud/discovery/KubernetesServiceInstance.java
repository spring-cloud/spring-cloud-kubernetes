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

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.net.URISyntaxException;

import static io.fabric8.kubernetes.client.utils.Utils.isNotNullOrEmpty;
import static io.fabric8.kubernetes.client.utils.Utils.isNullOrEmpty;

public class KubernetesServiceInstance implements ServiceInstance {

    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final String COLN = ":";

    private final String serviceId;
    private final String portName;
    private final EndpointSubset subset;
    private final Boolean secure;

    public KubernetesServiceInstance(String serviceId, String portName, EndpointSubset subset, Boolean secure) {
        this.serviceId = serviceId;
        this.portName = portName;
        this.subset = subset;
        this.secure = secure;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public String getHost() {
        if (subset.getAddresses().isEmpty()) {
            throw new IllegalStateException("Endpoint subset has no addresses.");
        }
        return subset.getAddresses().get(0).getIp();
    }

    @Override
    public int getPort() {
        if (subset.getPorts().isEmpty()) {
            throw new IllegalStateException("Endpoint subset has no ports.");
        } else if (isNullOrEmpty(portName) && subset.getPorts().size() == 1) {
            return subset.getPorts().get(0).getPort();
        } else if (isNotNullOrEmpty(portName)) {
            for (EndpointPort port : subset.getPorts()) {
                if (portName.endsWith(port.getName())) {
                    return port.getPort();
                }
            }
        }
        throw new IllegalStateException("Endpoint subset has no matching ports.");
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public URI getUri() {
        StringBuilder sb = new StringBuilder();

        if (isSecure()) {
            sb.append(HTTPS_PREFIX);
        } else {
            sb.append(HTTP_PREFIX);
        }

        sb.append(getHost()).append(COLN).append(getPort());
        try {
            return new URI(sb.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
