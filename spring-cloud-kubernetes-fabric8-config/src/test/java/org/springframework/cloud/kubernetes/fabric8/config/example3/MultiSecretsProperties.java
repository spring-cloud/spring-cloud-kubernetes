/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.example3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Haytham Mohamed
 **/

@ConfigurationProperties("secrets")
public class MultiSecretsProperties {

	private String secret1;

	private String secret2;

	public String getSecret1() {
		return secret1;
	}

	public void setSecret1(String secret1) {
		this.secret1 = secret1;
	}

	public String getSecret2() {
		return secret2;
	}

	public void setSecret2(String secret2) {
		this.secret2 = secret2;
	}

}
