package org.springframework.cloud.kubernetes.commons.config;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.getApplicationName;

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
	}

	protected Stream<NormalizedSource> normalize(String defaultName, String defaultNamespace,
		Map<String, String> defaultLabels, boolean defaultIncludeProfileSpecificSources, boolean failFast,
		boolean defaultUseNameAsPrefix, Environment environment) {

		Stream.Builder<NormalizedSource> normalizedSources = Stream.builder();

		String normalizedName = StringUtils.hasLength(this.name) ? this.name : defaultName;
		String normalizedNamespace = StringUtils.hasLength(this.namespace) ? this.namespace : defaultNamespace;
		Map<String, String> normalizedLabels = this.labels.isEmpty() ? defaultLabels : this.labels;

		String configMapName = getApplicationName(environment, normalizedName, "Config Map");

		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix(this.explicitPrefix, this.useNameAsPrefix,
			defaultUseNameAsPrefix, normalizedName);

		boolean includeProfileSpecificSources = ConfigUtils.includeProfileSpecificSources(
			defaultIncludeProfileSpecificSources, this.includeProfileSpecificSources);
		NormalizedSource namedBasedSource = new NamedConfigMapNormalizedSource(configMapName, normalizedNamespace,
			failFast, prefix, includeProfileSpecificSources);
		normalizedSources.add(namedBasedSource);

		if (!normalizedLabels.isEmpty()) {
			NormalizedSource labeledBasedSource = new LabeledConfigMapNormalizedSource(normalizedNamespace, labels,
				failFast, prefix, includeProfileSpecificSources);
			normalizedSources.add(labeledBasedSource);
		}

		return normalizedSources.build();

	}

}
