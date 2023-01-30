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
