package org.springframework.cloud.kubernetes.commons.discovery;

public record InstanceIdHostPodName(String instanceId, String host, String podName) {
}
