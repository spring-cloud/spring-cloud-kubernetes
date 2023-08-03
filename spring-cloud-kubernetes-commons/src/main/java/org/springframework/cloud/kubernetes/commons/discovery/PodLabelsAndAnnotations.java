package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.Map;

public record PodLabelsAndAnnotations(Map<String, String> labels, Map<String, String> annotations) {
}
