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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.io.IOException;

import io.fabric8.kubernetes.client.utils.IOHelpers;

final class ConfigMapTestUtil {

	private ConfigMapTestUtil() {
	}

	static String readResourceFile(String file) {
		String resource;
		try {
			resource = IOHelpers.readFully(ConfigMapTestUtil.class.getClassLoader().getResourceAsStream(file));
		}
		catch (IOException e) {
			resource = "";
		}
		return resource;
	}

}
