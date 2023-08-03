package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.Map;

public record ServiceMetadataForServiceInstance(String name, Map<String, String> labels,
	Map<String, String> annotation) {
}
