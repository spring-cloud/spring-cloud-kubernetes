package org.springframework.cloud.kubernetes.condition;

import java.util.HashMap;
import java.util.Map;

final class VersionInfoUtil {

	private VersionInfoUtil() {
	}

	static Map create(int major, int minor) {
		return new HashMap<String, String>() {{
			put("major", "");
			put("minor", "");
			put("gitVersion", String.format("v%d.%d.2", major, minor));
			put("buildDate", "2018-05-09T19:21:16Z");
			put("goVersion", "1.9");
			put("compiler", "compiler");
			put("platform", "linux/amd64");
			put("gitCommit", "2343534");
		}};
	}
}
