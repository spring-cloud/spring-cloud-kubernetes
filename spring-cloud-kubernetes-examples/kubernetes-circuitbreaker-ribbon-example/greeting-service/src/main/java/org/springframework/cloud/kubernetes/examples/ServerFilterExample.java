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

package org.springframework.cloud.kubernetes.examples;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.ribbon.KubernetesEndpointsServerFilter;
import org.springframework.stereotype.Component;

import com.netflix.client.config.IClientConfigKey;

import io.fabric8.kubernetes.api.model.EndpointAddress;
/**
 * Kubernetes implementation of a Ribbon {@link IClientConfigKey}.
 *
 * @param <T> type of key
 * @author  fuheping
 */

@Component
public class ServerFilterExample implements KubernetesEndpointsServerFilter {

	private static final Log LOG = LogFactory.getLog(ServerFilterExample.class);

	@Override
	public boolean isFilter(String serviceId, EndpointAddress adress) {
		LOG.debug("*******************serviceId:" + serviceId);
		LOG.debug("*******************adress:" + adress);
		
		// TODO Auto-generated method stub
		if (!serviceId.equals("nameservice"))
			return true;
		if (adress.getTargetRef().getName().startsWith("service2")) 
			return true;
		return false;
	}

}
