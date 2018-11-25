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

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import me.snowdrop.istio.client.IstioClient;
import me.snowdrop.istio.mixer.template.apikey.ApiKey;
import me.snowdrop.istio.mixer.template.apikey.ApiKeyList;
import me.snowdrop.istio.mixer.template.apikey.DoneableApiKey;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


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
			MixedOperation<ApiKey, ApiKeyList, DoneableApiKey, Resource<ApiKey, DoneableApiKey>> apiKeyApiKeyListDoneableApiKeyResourceMixedOperation = client.mixer().apiKey();
			ApiKeyList list = apiKeyApiKeyListDoneableApiKeyResourceMixedOperation.list();
			for (ApiKey ak : list.getItems()) {
				LOG.info(">>> API Keys listed inside pod: " + ak.toString());
			}
			return true;
		} catch (Throwable t) {
			LOG.warn("Failed to get Istio Resources. Are you missing serviceaccount permissions?", t);
			return false;
		}
	}


}
