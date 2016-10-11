package io.fabric8.spring.cloud.kubernetes.reload;

import java.util.function.Function;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;

/**
 * Provides a way to start Kubernetes watches and bind their lifecycle to the application context.
 */
public interface EventWatcher {

    void addWatch(String name, Function<KubernetesClient, Watch> watch);

}
