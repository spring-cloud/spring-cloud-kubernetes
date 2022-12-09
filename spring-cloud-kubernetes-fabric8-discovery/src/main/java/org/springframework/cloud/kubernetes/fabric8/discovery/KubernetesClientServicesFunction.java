/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.function.Function;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.ServiceResource;

/**
 * A regular java.util.function that is used to hide the complexity of the
 * KubernetesClient interfaces.
 *
 * It's meant to be used to abstract things like:
 *
 * client.services() client.services().withLabel("key", "value")
 * client.services().withoutLabel("key")
 *
 * The result of the application of the function can then be used for example to list the
 * services like so:
 *
 * function.apply(client).list()
 *
 * See KubernetesDiscoveryClientAutoConfiguration.servicesFunction
 *
 * @author Georgios Andrianakis
 */
public interface KubernetesClientServicesFunction
		extends Function<KubernetesClient, FilterWatchListDeletable<Service, ServiceList, ServiceResource<Service>>> {

}
