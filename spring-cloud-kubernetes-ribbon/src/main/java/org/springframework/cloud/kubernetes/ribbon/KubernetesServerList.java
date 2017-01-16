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

package org.springframework.cloud.kubernetes.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Utils;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KubernetesServerList extends AbstractServerList<Server> implements ServerList<Server> {

    private static final int FIRST = 0;
    private static final Log LOG = LogFactory.getLog(KubernetesServerList.class);

    private final KubernetesClient client;

    private String serviceId;

    private String namespace;
    private String portName;


    public KubernetesServerList(KubernetesClient client) {
        this.client = client;
    }

    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.serviceId = clientConfig.getClientName();
        this.namespace = clientConfig.getPropertyAsString(KubernetesConfigKey.Namespace, client.getNamespace());
        this.portName = clientConfig.getPropertyAsString(KubernetesConfigKey.PortName, null);
    }

    public List<Server> getInitialListOfServers() {
        return Collections.emptyList();
    }

    public List<Server> getUpdatedListOfServers() {
        Endpoints endpoints = namespace != null
                ? client.endpoints().inNamespace(namespace).withName(serviceId).get()
                : client.endpoints().withName(serviceId).get();

        List<Server> result = new ArrayList<Server>();
        if (endpoints != null) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Found [" + endpoints.getSubsets().size() + "] endpoints in namespace [" +
                        namespace + "] for name [" + serviceId + "] and portName [" + portName + "]");
            }
            for (EndpointSubset subset : endpoints.getSubsets()) {

                if (subset.getPorts().size() == 1) {
                    EndpointPort port = subset.getPorts().get(FIRST);
                    for (EndpointAddress address : subset.getAddresses()) {
                        result.add(new Server(address.getIp(), port.getPort()));
                    }
                } else {
                    for (EndpointPort port : subset.getPorts()) {
                        if (Utils.isNullOrEmpty(portName) || portName.endsWith(port.getName())) {
                            for (EndpointAddress address : subset.getAddresses()) {
                                result.add(new Server(address.getIp(), port.getPort()));
                            }
                        }
                    }
                }
            }
        } else {
            LOG.warn("Did not find any endpoints in ribbon in namespace [" + namespace + "] for name [" +
                    serviceId + "] and portName [" + portName + "]");
        }
        return result;
    }
}
