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

package org.springframework.cloud.kubernetes.fabric8;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.cloud.kubernetes.commons.AbstractKubernetesHealthIndicator;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.util.CollectionUtils;

/**
 * Kubernetes implementation of {@link AbstractHealthIndicator}.
 *
 * @author Ioannis Canellos
 * @author Eddú Meléndez
 */
public class Fabric8HealthIndicator extends AbstractKubernetesHealthIndicator {

	private final PodUtils<Pod> utils;

	public Fabric8HealthIndicator(PodUtils<Pod> utils) {
		this.utils = utils;
	}

	@Override
	protected Map<String, Object> getDetails() {
		Pod current = this.utils.currentPod().get();
		if (current != null) {
			Map<String, Object> details = CollectionUtils.newHashMap(8);
			details.put(INSIDE, true);

			ObjectMeta metadata = current.getMetadata();
			details.put(NAMESPACE, metadata.getNamespace());
			details.put(POD_NAME, metadata.getName());
			details.put(LABELS, metadata.getLabels());

			PodStatus status = current.getStatus();
			details.put(POD_IP, status.getPodIP());
			details.put(HOST_IP, status.getHostIP());

			PodSpec spec = current.getSpec();
			details.put(SERVICE_ACCOUNT, spec.getServiceAccountName());
			details.put(NODE_NAME, spec.getNodeName());

			return details;
		}

		return Collections.singletonMap(INSIDE, false);
	}

}
