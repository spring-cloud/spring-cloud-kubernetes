/*
 * Copyright (C) 2017 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Secrets and ConfigMaps shared features.
 */
public class KubernetesPropertySource extends MapPropertySource {
	private static final Log LOG = LogFactory.getLog(KubernetesPropertySource.class);

	@SuppressWarnings("unchecked")
	protected KubernetesPropertySource(String name, Map<String, Object> source) {
		super(name, source);
	}

	protected static void putPathConfig(Map<String, ? super String> result, List<String> paths) {
		paths
			.stream()
			.map(Paths::get)
			.filter(Files::exists)
			.forEach(p -> putAll(p, result));
	}

	private static void putAll(Path path, Map<String, ? super String> result) {
		try {
			Files.walk(path)
				.filter(Files::isRegularFile)
				.forEach(p -> readFile(p, result));
		} catch (IOException e) {
			LOG.warn("Error walking properties files", e);
		}
	}

	private static void readFile(Path path, Map<String, ? super String> result) {
		try {
			result.put(
				path.getFileName().toString(),
				new String(Files.readAllBytes(path)).trim());
		} catch (IOException e) {
			LOG.warn("Error reading properties file", e);
		}
	}
}
