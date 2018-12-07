/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.springframework.cloud.kubernetes.istio;

import io.fabric8.kubernetes.client.Config;
import me.snowdrop.istio.client.DefaultIstioClient;
import me.snowdrop.istio.client.IstioClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.KubernetesAutoConfiguration;
import org.springframework.cloud.kubernetes.istio.utils.MeshUtils;
import org.springframework.cloud.kubernetes.istio.utils.StandardMeshUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.kubernetes.istio.enabled", matchIfMissing = true)
public class IstioAutoConfiguration {

	private static final Log LOG = LogFactory.getLog(KubernetesAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public IstioClient istioClient(Config config) {
		return new DefaultIstioClient(config);
	}

	@Bean
	@ConditionalOnMissingBean
	public MeshUtils istioMeshhUtils(IstioClient client) {
		return new StandardMeshUtils(client);
	}

}
