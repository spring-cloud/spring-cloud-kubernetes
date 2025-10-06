package org.springframework.cloud.kubernetes.commons.config;

import java.util.List;
import java.util.Map;

abstract sealed class SourceConfigProperties permits ConfigMapConfigProperties {

	private final boolean enabled;

	List<ConfigMapConfigProperties.Source> sources = List.of();

	Map<String, String> labels = Map.of();

	String name;

	String namespace;

	boolean useNameAsPrefix;

	boolean includeProfileSpecificSources = true;

	boolean failFast;

	RetryProperties retry = RetryProperties.DEFAULT;

	ReadType readType = ReadType.BATCH;

	SourceConfigProperties(boolean enabled) {
		this.enabled = enabled;
	}

	public List<ConfigMapConfigProperties.Source> getSources() {
		return sources;
	}

	public void setSources(List<ConfigMapConfigProperties.Source> sources) {
		this.sources = sources;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public boolean isUseNameAsPrefix() {
		return useNameAsPrefix;
	}

	public void setUseNameAsPrefix(boolean useNameAsPrefix) {
		this.useNameAsPrefix = useNameAsPrefix;
	}

	public boolean isIncludeProfileSpecificSources() {
		return includeProfileSpecificSources;
	}

	public void setIncludeProfileSpecificSources(boolean includeProfileSpecificSources) {
		this.includeProfileSpecificSources = includeProfileSpecificSources;
	}

	public boolean isFailFast() {
		return failFast;
	}

	public void setFailFast(boolean failFast) {
		this.failFast = failFast;
	}

	public RetryProperties getRetry() {
		return retry;
	}

	public void setRetry(RetryProperties retry) {
		this.retry = retry;
	}

	public ReadType getReadType() {
		return readType;
	}

	public void setReadType(ReadType readType) {
		this.readType = readType;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
