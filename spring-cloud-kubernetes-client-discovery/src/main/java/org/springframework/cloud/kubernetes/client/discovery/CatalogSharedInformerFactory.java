/*
 * Copyright 2019-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.spring.extended.controller.annotation.GroupVersionResource;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformer;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformers;

@KubernetesInformers({
		@KubernetesInformer(apiTypeClass = V1Service.class, apiListTypeClass = V1ServiceList.class,
				groupVersionResource = @GroupVersionResource(apiGroup = "", apiVersion = "v1",
						resourcePlural = "services")),
		@KubernetesInformer(apiTypeClass = V1Endpoints.class, apiListTypeClass = V1EndpointsList.class,
				groupVersionResource = @GroupVersionResource(apiGroup = "", apiVersion = "v1",
						resourcePlural = "endpoints")) })
public class CatalogSharedInformerFactory extends SharedInformerFactory {

	// TODO: optimization to ease memory pressure from continuous list&watch.

}
