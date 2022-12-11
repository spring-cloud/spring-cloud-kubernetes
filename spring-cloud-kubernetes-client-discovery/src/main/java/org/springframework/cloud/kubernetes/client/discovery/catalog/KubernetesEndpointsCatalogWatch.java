package org.springframework.cloud.kubernetes.client.discovery.catalog;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.core.log.LogAccessor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class KubernetesEndpointsCatalogWatch
	implements Function<KubernetesCatalogWatch, List<EndpointNameAndNamespace>> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesEndpointsCatalogWatch.class));

	@Override
	public List<EndpointNameAndNamespace> apply(KubernetesCatalogWatchContext context) {
		// take only pods that have endpoints
		List<V1Endpoints> endpoints;
		if (context.properties().allNamespaces()) {
			LOG.debug(() -> "discovering endpoints in all namespaces");

			// can't use try with resources here as it will close the client
			CoreV1Api client = context.client();
			endpoints = endpoints(client);
		} else {
			String namespace = KubernetesClientUtils
			LOG.debug(() -> "fabric8 catalog watcher will use namespace : " + namespace);

			// can't use try with resources here as it will close the client
			KubernetesClient client = context.kubernetesClient();
			endpoints = client.endpoints().inNamespace(namespace).withLabels(context.properties().serviceLabels())
				.list().getItems();
		}

		/**
		 * <pre>
		 *   - An "Endpoints" holds a List of EndpointSubset.
		 *   - A single EndpointSubset holds a List of EndpointAddress
		 *
		 *   - (The union of all EndpointSubsets is the Set of all Endpoints)
		 *   - Set of Endpoints is the cartesian product of :
		 *     EndpointSubset::getAddresses and EndpointSubset::getPorts (each is a List)
		 * </pre>
		 */
		Stream<ObjectReference> references = endpoints.stream().map(Endpoints::getSubsets).filter(Objects::nonNull)
			.flatMap(List::stream).map(EndpointSubset::getAddresses).filter(Objects::nonNull).flatMap(List::stream)
			.map(EndpointAddress::getTargetRef);

		return Fabric8CatalogWatchContext.state(references);

	}

	private List<V1Endpoints> endpoints(CoreV1Api client) {
		try {
			return client.listEndpointsForAllNamespaces(null, null, null, null, null, null, null, null, null, null)
				.getItems();
		} catch (ApiException e) {
			LOG.warn(e, () -> "can not list endpoints in all namespaces");
			return Collections.emptyList();
		}
	}

}
