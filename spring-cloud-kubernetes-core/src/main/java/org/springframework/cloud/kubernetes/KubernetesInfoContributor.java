/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes;

import io.fabric8.kubernetes.api.model.Pod;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;

public class KubernetesInfoContributor implements InfoContributor {

	private static final Log LOG = LogFactory.getLog(KubernetesInfoContributor.class);
	
	private PodUtils utils;

	public KubernetesInfoContributor(PodUtils utils) {
		this.utils = utils;
	}

	@Override
	public void contribute(Builder builder) {
		try {
			Pod current = utils.currentPod().get();
			Map<String, Object> details = new HashMap<>();
			if (current != null) {
				details.put("inside", true);
				details.put("namespace", current.getMetadata().getNamespace());
				details.put("podName", current.getMetadata().getName());
				details.put("podIp", current.getStatus().getPodIP());
				details.put("serviceAccount", current.getSpec().getServiceAccountName());
				details.put("nodeName", current.getSpec().getNodeName());
				details.put("hostIp", current.getStatus().getHostIP());
			}
			else {
				details.put("inside", false);
			}
			builder.withDetail("kubernetes", details);
		}
		catch (Exception e) {
			LOG.warn("Failed to get pod details", e);
		}
	}
}
