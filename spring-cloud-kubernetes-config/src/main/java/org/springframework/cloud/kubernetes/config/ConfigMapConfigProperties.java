/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import java.util.LinkedList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.kubernetes.config")
public class ConfigMapConfigProperties extends AbstractConfigProperties {

    private static final String TARGET = "Config Map";

	private boolean enableApi = true;
	private List<String> paths = new LinkedList<>();

	public boolean isEnableApi() {
		return enableApi;
	}

	public void setEnableApi(boolean enableApi) {
		this.enableApi = enableApi;
	}

	public void setPaths(List<String> paths) {
		this.paths = paths;
	}

	public List<String> getPaths() {
		return paths;
	}

	@Override
    public String getConfigurationTarget() {
        return TARGET;
    }
}
