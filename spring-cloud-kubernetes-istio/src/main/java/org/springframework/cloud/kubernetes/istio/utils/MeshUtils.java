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

package org.springframework.cloud.kubernetes.istio.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.kubernetes.istio.IstioClientProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Utility class to work with meshes.
 *
 * @author Mauricio Salatino
 */
public class MeshUtils {

	private static final Log LOG = LogFactory.getLog(MeshUtils.class);

	private final IstioClientProperties istioClientProperties;

	private RestTemplate restTemplate = new RestTemplateBuilder().build();

	public MeshUtils(IstioClientProperties istioClientProperties) {
		this.istioClientProperties = istioClientProperties;
	}

	public Boolean isIstioEnabled() {
		return checkIstioServices();
	}

	private synchronized boolean checkIstioServices() {
		try {
			// Check if Istio Envoy proxy is installed. Notice that the check is done to
			// localhost.
			// TODO: We can improve this initial detection if better methods are found.
			String resource = "http://localhost:" + this.istioClientProperties.getEnvoyPort();
			ResponseEntity<String> response = this.restTemplate
					.getForEntity(resource + "/" + this.istioClientProperties.getTestPath(), String.class);
			if (response.getStatusCode().is2xxSuccessful()) {
				LOG.info("Istio Resources Found.");
				return true;
			}
			LOG.warn("Although Envoy proxy did respond at port" + this.istioClientProperties.getEnvoyPort()
					+ ", it did not respond with HTTP 200 to path: " + this.istioClientProperties.getTestPath()
					+ ". You may need to tweak the test path in order to get proper Istio support");
			return false;
		}
		catch (Throwable t) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Envoy proxy could not be located at port: " + this.istioClientProperties.getEnvoyPort()
						+ ". Assuming that the application is not running inside the Istio Service Mesh");
			}
			return false;
		}
	}

}
