package org.springframework.cloud.kubernetes.config;

import io.fabric8.kubernetes.client.utils.IOHelpers;
import java.io.IOException;

final class ConfigMapTestUtil {

	private ConfigMapTestUtil() {
	}


	static String readResourceFile(String file) {
		String resource;
		try {
			resource = IOHelpers.readFully(
				ConfigMapTestUtil.class.getClassLoader().getResourceAsStream(file));
		}
		catch (IOException e) {
			resource = "";
		}
		return resource;
	}
}
