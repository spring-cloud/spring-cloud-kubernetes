package org.springframework.cloud.kubernetes.commons.config;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.getApplicationName;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.Prefix;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.findPrefix;
import static org.springframework.util.StringUtils.hasLength;

abstract sealed class SourceConfigProperties permits ConfigMapConfigProperties {

	private final boolean enabled;
	private final List<Source> sources;
	private final Map<String, String> labels;
	private final String name;
	private final String namespace;
	private final boolean useNameAsPrefix;
	private final boolean includeProfileSpecificSources;
	private final boolean failFast;
	private final RetryProperties retry;
	private final ReadType readType;

	public SourceConfigProperties(@DefaultValue("true") boolean enabled,
			@DefaultValue List<Source> sources, @DefaultValue Map<String, String> labels,
			String name, String namespace, boolean useNameAsPrefix,
			@DefaultValue("true") boolean includeProfileSpecificSources, boolean failFast,
			@DefaultValue RetryProperties retry, @DefaultValue("BATCH") ReadType readType) {

		this.enabled = enabled;
		this.sources = sources;
		this.labels = labels;
		this.name = name;
		this.namespace = namespace;
		this.useNameAsPrefix = useNameAsPrefix;
		this.includeProfileSpecificSources = includeProfileSpecificSources;
		this.failFast = failFast;
		this.retry = retry;
		this.readType = readType;
	}

	/**
	 * Config map source.
	 *
	 * @param name The name of the ConfigMap.
	 * @param namespace The namespace where the ConfigMap is found.
	 * @param labels labels of the config map to look for against.
	 * @param explicitPrefix An explicit prefix to be used for properties.
	 * @param useNameAsPrefix Use config map name as prefix for properties.
	 * @param includeProfileSpecificSources Use profile name to append to a config map
	 * name.
	 */
	public record Source(String name, String namespace, @DefaultValue Map<String, String> labels, String explicitPrefix,
		Boolean useNameAsPrefix, Boolean includeProfileSpecificSources) {

		Stream<NormalizedSource> normalize(boolean configMap, String defaultName, String defaultNamespace,
			Map<String, String> defaultLabels, boolean defaultIncludeProfileSpecificSources, boolean failFast,
			boolean defaultUseNameAsPrefix, Environment environment) {

			Stream.Builder<NormalizedSource> normalizedSources = Stream.builder();

			String normalizedName = hasLength(name) ? name : defaultName;
			String normalizedNamespace = hasLength(namespace) ? namespace : defaultNamespace;
			Map<String, String> normalizedLabels = labels.isEmpty() ? defaultLabels : labels;

			String configurationTarget = configMap ? "ConfigMap" : "Secret";
			String sourceName = getApplicationName(environment, normalizedName, configurationTarget);

			Prefix prefix = findPrefix(explicitPrefix, useNameAsPrefix,
				defaultUseNameAsPrefix, normalizedName);

			boolean includeProfileSpecificSources = ConfigUtils.includeProfileSpecificSources(
				defaultIncludeProfileSpecificSources, this.includeProfileSpecificSources);

			NormalizedSource namedSource;
			if (configMap) {
				namedSource = new NamedConfigMapNormalizedSource(sourceName, normalizedNamespace,
					failFast, prefix, includeProfileSpecificSources);
			} else {
				namedSource = new NamedSecretNormalizedSource(sourceName, normalizedNamespace,
					failFast, prefix, includeProfileSpecificSources);
				normalizedSources.add(namedSource);
			}

			normalizedSources.add(namedSource);

			if (!normalizedLabels.isEmpty()) {
				NormalizedSource labeledSource;
				if (configMap) {
					labeledSource = new LabeledConfigMapNormalizedSource(normalizedNamespace, labels,
						failFast, prefix, includeProfileSpecificSources);
				} else {
					labeledSource = new LabeledSecretNormalizedSource(normalizedNamespace, labels,
						failFast, prefix);
					normalizedSources.add(labeledSource);
				}
				normalizedSources.add(labeledSource);

			}

			return normalizedSources.build();

		}
	}

	public List<NormalizedSource> determineSources(boolean configMap, Environment environment) {
		if (getSources().isEmpty()) {
			List<NormalizedSource> result = new ArrayList<>(2);
			String configurationTarget = configMap ? "ConfigMap" : "Secret";
			String name = getApplicationName(environment, getName(), configurationTarget);
			NormalizedSource normalizedSource;
			if (configMap) {
				normalizedSource = new NamedConfigMapNormalizedSource(name, getNamespace(),
					isFailFast(), isIncludeProfileSpecificSources());
			} else {
				normalizedSource = new NamedSecretNormalizedSource(name, this.namespace, this.failFast,
					this.includeProfileSpecificSources);
			}
			result.add(normalizedSource);

			if (!getLabels().isEmpty()) {
				NormalizedSource labeledSource;
				if (configMap) {
					labeledSource = new LabeledConfigMapNormalizedSource(getNamespace(), getLabels(), isFailFast(),
						ConfigUtils.Prefix.DEFAULT, false);
				} else {
					labeledSource = new LabeledSecretNormalizedSource(this.namespace, this.labels, this.failFast,
						ConfigUtils.Prefix.DEFAULT);
				}
				result.add(labeledSource);
			}
			return result;
		}

		return getSources().stream()
			.flatMap(s -> s.normalize(configMap, getName(), getNamespace(), getLabels(),
				isIncludeProfileSpecificSources(), isFailFast(), isUseNameAsPrefix(), environment))
			.toList();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public List<Source> getSources() {
		return sources;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public String getName() {
		return name;
	}

	public String getNamespace() {
		return namespace;
	}

	public boolean isUseNameAsPrefix() {
		return useNameAsPrefix;
	}

	public boolean isIncludeProfileSpecificSources() {
		return includeProfileSpecificSources;
	}

	public boolean isFailFast() {
		return failFast;
	}

	public RetryProperties getRetry() {
		return retry;
	}

	public ReadType getReadType() {
		return readType;
	}
}
