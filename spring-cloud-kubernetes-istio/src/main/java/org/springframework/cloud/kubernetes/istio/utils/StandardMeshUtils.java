/*
 *     Copyright (C) 2016 to the original authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.cloud.kubernetes.istio.utils;

import me.snowdrop.istio.client.IstioClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.kubernetes.istio.IstioClientProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;


public class StandardMeshUtils implements MeshUtils {

	private static final Log LOG = LogFactory.getLog(StandardMeshUtils.class);

	private final IstioClient client;
	private final IstioClientProperties istioClientProperties;

	public StandardMeshUtils(IstioClient client, IstioClientProperties istioClientProperties) {
		if (client == null) {
			throw new IllegalArgumentException("Must provide an instance of IstioClient");
		}
		this.client = client;
		this.istioClientProperties = istioClientProperties;
	}


	@Override
	public Boolean isIstioEnabled() {
		return checkIstioServices();
	}

	private synchronized boolean checkIstioServices() {
		try {
			//Check if Istio Envoy proxy is installed. Notice that the check is done to localhost.
			// TODO: We can improve this initial detection if better methods are found.
			RestTemplate restTemplate = new RestTemplateBuilder().build();
			String resource = "http://localhost:" + istioClientProperties.getEnvoyPort();
			ResponseEntity<String> response = restTemplate.getForEntity(resource + "/" + istioClientProperties.getTestPath(), String.class);
			if (response.getStatusCode().is2xxSuccessful()) {
				LOG.info("Istio Resources Found.");
				return true;
			}
			LOG.warn("Failed to get Istio Resources.");
			return false;
		} catch (Throwable t) {
			LOG.warn("Failed to get Istio Resources. Are you missing serviceaccount permissions? Are Istio Services up?", t);
			return false;
		}
	}


}
