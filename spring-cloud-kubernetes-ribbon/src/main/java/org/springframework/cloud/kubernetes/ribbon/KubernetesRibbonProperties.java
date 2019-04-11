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

package org.springframework.cloud.kubernetes.ribbon;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * the KubernetesRibbonProperties description.
 *
 * @author wuzishu
 */
@ConfigurationProperties(prefix = "spring.cloud.kubernetes.ribbon")
public class KubernetesRibbonProperties {

	/**
	 * {@link KubernetesRibbonMode} setting ribbon server list with ip of pod or service
	 * name. default value is POD.
	 */
	private KubernetesRibbonMode mode;

	/**
	 * Gets mode.
	 * @return the mode
	 */
	public KubernetesRibbonMode getMode() {
		return mode;
	}

	/**
	 * Sets mode.
	 * @param mode the mode
	 */
	public void setMode(KubernetesRibbonMode mode) {
		this.mode = mode;
	}

}
