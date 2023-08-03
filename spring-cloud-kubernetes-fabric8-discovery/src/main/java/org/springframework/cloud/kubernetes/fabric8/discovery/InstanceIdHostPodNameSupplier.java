package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Service;
import org.springframework.cloud.kubernetes.commons.discovery.InstanceIdHostPodName;

import java.util.Optional;
import java.util.function.Supplier;

final class InstanceIdHostPodNameSupplier implements Supplier<InstanceIdHostPodName> {

	private final EndpointAddress endpointAddress;

	private final Service service;

	InstanceIdHostPodNameSupplier(EndpointAddress endpointAddress, Service service) {
		this.endpointAddress = endpointAddress;
		this.service = service;
	}

	@Override
	public InstanceIdHostPodName get() {
		return new InstanceIdHostPodName(instanceId(), host(), podName());
	}

	// instanceId is usually the pod-uid as seen in the .metadata.uid
	private String instanceId() {
		return Optional.ofNullable(endpointAddress).map(EndpointAddress::getTargetRef)
			.map(ObjectReference::getUid).orElseGet(() -> service.getMetadata().getUid());
	}

	private String host() {
		return Optional.ofNullable(endpointAddress).map(EndpointAddress::getIp)
			.orElseGet(() -> service.getSpec().getExternalName());
	}

	private String podName() {
		return Optional.ofNullable(endpointAddress).map(EndpointAddress::getTargetRef)
			.filter(objectReference -> "Pod".equals(objectReference.getKind()))
			.map(ObjectReference::getName).orElse(null);
	}
}
