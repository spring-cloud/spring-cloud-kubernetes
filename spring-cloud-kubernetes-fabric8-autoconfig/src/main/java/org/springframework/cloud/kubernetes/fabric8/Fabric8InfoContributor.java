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

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Pod;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.cloud.kubernetes.commons.AbstractKubernetesInfoContributor;
import org.springframework.cloud.kubernetes.commons.PodUtils;

/**
 * Kubernetes implementation of {@link InfoContributor}.
 *
 * @author Mark Anderson
 */
public class Fabric8InfoContributor extends AbstractKubernetesInfoContributor {

	private PodUtils<Pod> utils;

	public Fabric8InfoContributor(PodUtils<Pod> utils) {
		this.utils = utils;
	}

	@Override
	public Map<String, Object> getDetails() {
		Pod current = this.utils.currentPod().get();
		Map<String, Object> details = new HashMap<>();
		boolean inside = current != null;
		details.put(INSIDE, inside);
		if (inside) {
			details.put(NAMESPACE, current.getMetadata().getNamespace());
			details.put(POD_NAME, current.getMetadata().getName());
			details.put(POD_IP, current.getStatus().getPodIP());
			details.put(SERVICE_ACCOUNT, current.getSpec().getServiceAccountName());
			details.put(NODE_NAME, current.getSpec().getNodeName());
			details.put(HOST_IP, current.getStatus().getHostIP());
		}
		return details;
	}

}
