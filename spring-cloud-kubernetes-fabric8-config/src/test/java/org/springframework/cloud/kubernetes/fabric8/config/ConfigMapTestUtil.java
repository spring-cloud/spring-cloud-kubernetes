/*
 * Copyright 2013-2024 the original author or authors.
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import io.fabric8.kubernetes.client.utils.IOHelpers;

public final class ConfigMapTestUtil {

	private ConfigMapTestUtil() {
	}

	public static String readResourceFile(String file) {
		String resource;
		try {
			resource = IOHelpers
				.readFully(Objects.requireNonNull(ConfigMapTestUtil.class.getClassLoader().getResourceAsStream(file)));
		}
		catch (IOException e) {
			resource = "";
		}
		return resource;
	}

	public static void createFileWithContent(String file, String content) throws IOException {
		Files.write(Paths.get(file), content.getBytes(), StandardOpenOption.CREATE);
	}

}
