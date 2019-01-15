package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;

import java.util.function.Function;

/**
 * A regular java.util.function that is used to hide the complexity of the KubernetesClient interfaces
 *
 * It's meant to be used to abstract things like:
 *
 * client.services()
 * client.services().withLabel("key", "value")
 * client.services().withoutLabel("key")
 *
 * The result of the application of the function can then be used for example to list the services like so:
 *
 * function.apply(client).list()
 *
 * See KubernetesDiscoveryClientAutoConfiguration.servicesFunction
 */
public interface KubernetesClientServicesFunction extends
	Function<KubernetesClient, FilterWatchListDeletable<Service, ServiceList, Boolean, Watch, Watcher<Service>>> {
}
