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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;


public class StandardMeshUtils implements MeshUtils {

	private static final Log LOG = LogFactory.getLog(StandardMeshUtils.class);

	private final IstioClient client;

	public StandardMeshUtils(IstioClient client) {
		if (client == null) {
			throw new IllegalArgumentException("Must provide an instance of IstioClient");
		}
		this.client = client;
	}


	@Override
	public Boolean isIstioEnabled() {
		return checkIstioServices();
	}

	private synchronized boolean checkIstioServices() {
		try {
			WebClient client = WebClient.create("http://localhost:15090/");
			ClientResponse clientResponse = client.get().uri("stats/prometheus").exchange().block();
			if (clientResponse.statusCode().is2xxSuccessful()) {
				return true;
			}
			return false;
		} catch (Throwable t) {
			LOG.warn("Failed to get Istio Resources. Are you missing serviceaccount permissions?", t);
			return false;
		}
	}


}
