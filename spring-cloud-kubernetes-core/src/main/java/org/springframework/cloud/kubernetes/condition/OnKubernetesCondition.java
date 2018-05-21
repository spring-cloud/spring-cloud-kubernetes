package org.springframework.cloud.kubernetes.condition;

import static org.springframework.cloud.kubernetes.KubernetesClientProperties.SPRING_CLOUD_KUBERNETES_CLIENT_PROPS;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.kubernetes.condition.ConditionalOnKubernetes.Range;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnKubernetesCondition extends SpringBootCondition {


	private static final String ANNOTATION_VERSION_REGEX = "(\\d+)\\.(\\d+)";
	private static final Pattern GIT_VERSION_PATTERN = Pattern.compile("v(\\d+)\\.(\\d+).*");
	private static final Pattern ANNOTATION_VERSION_PATTERN =
		Pattern.compile(ANNOTATION_VERSION_REGEX);

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
		AnnotatedTypeMetadata metadata) {

		ConditionMessage.Builder message = ConditionMessage.forCondition("");

		if (metadata.isAnnotated(ConditionalOnKubernetes.class.getName())) {
			KubernetesClient kubernetesClient = getKubernetesClient(context.getEnvironment());

			if (!isServerUp(kubernetesClient)) {
				return ConditionOutcome.noMatch(
					message.because("Could not communicate with the Kubernetes server at: "
						+ kubernetesClient.getMasterUrl())
				);
			}

			Map<String, Object> attributes = metadata
				.getAnnotationAttributes(ConditionalOnKubernetes.class.getName());

			ConditionOutcome adaptabilityCheckOutcome = checkAdaptability(
				(Class<?>) attributes.get("classClientMustAdaptTo"),
				kubernetesClient
			);

			if (adaptabilityCheckOutcome.isMatch()) {
				return checkVersion(
					(String) attributes.get("version"),
					(ConditionalOnKubernetes.Range) attributes.get("range"),
					kubernetesClient
				);
			}
			else {
				return adaptabilityCheckOutcome;
			}
		}

		return ConditionOutcome.noMatch(
			message.because("Unknown Conditional annotation using " +
				this.getClass().getSimpleName())
		);
	}

	// We need to create a KubernetesClient ourselves
	// since the KubernetesClient Bean is not available when the Condition is evaluated
	private KubernetesClient getKubernetesClient(PropertyResolver propertyResolver) {
		Config base = Config.autoConfigure(null);
		Config properties = new ConfigBuilder(base)
			.withMasterUrl(propertyResolver.getProperty(
				SPRING_CLOUD_KUBERNETES_CLIENT_PROPS + ".master-url",
				base.getMasterUrl()
				)
			)
			.withCaCertFile(propertyResolver.getProperty(
				SPRING_CLOUD_KUBERNETES_CLIENT_PROPS + ".ca-cert-file",
				base.getCaCertFile()
				)
			)
			.withCaCertData(propertyResolver.getProperty(
				SPRING_CLOUD_KUBERNETES_CLIENT_PROPS + ".ca-cert-data",
				base.getCaCertData()
				)
			)
			.build();

		return new DefaultKubernetesClient(properties);
	}

	private boolean isServerUp(KubernetesClient kubernetesClient) {
		try {
			kubernetesClient.rootPaths();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private ConditionOutcome checkAdaptability(Class<?> classClientMustAdaptTo,
		KubernetesClient kubernetesClient) {

		ConditionMessage.Builder message = ConditionMessage.forCondition("");

		if (classClientMustAdaptTo == Void.class) {
			return ConditionOutcome.match();
		}

		boolean isClientAdaptableToClass = kubernetesClient.isAdaptable(classClientMustAdaptTo);
		if (!isClientAdaptableToClass) {
			return ConditionOutcome.noMatch(
				message.because("The Kubernetes client was not adaptable to class "
					+ classClientMustAdaptTo)
			);
		}

		return ConditionOutcome.match();
	}

	private ConditionOutcome checkVersion(String annotationVersion,
		ConditionalOnKubernetes.Range range, KubernetesClient kubernetesClient) {

		ConditionMessage.Builder message = ConditionMessage.forCondition("");

		if (annotationVersion.isEmpty()) {
			return ConditionOutcome.match();
		}

		if(!annotationVersion.matches(ANNOTATION_VERSION_REGEX)) {
			return ConditionOutcome.noMatch(message.because("Input version " +
				annotationVersion + " does not match required major.minor format"));
		}

		try {
			VersionInfo versionInfo = kubernetesClient.getVersion();
			String gitVersion = versionInfo.getGitVersion();

			Matcher gitVersionMatcher = GIT_VERSION_PATTERN.matcher(gitVersion);

			if (gitVersionMatcher.find()) {
				String canonicalMajorMinorFromServer =
					canonicalMajorMinor(gitVersionMatcher.group(1), gitVersionMatcher.group(2));

				Matcher annotationVersionMatcher =
					ANNOTATION_VERSION_PATTERN.matcher(annotationVersion);

				//will always be true due to the matches check performed above
				if (annotationVersionMatcher.find()) {
					String annotationCanonicalMajorMinor =
						canonicalMajorMinor(
							annotationVersionMatcher.group(1),
							annotationVersionMatcher.group(2)
						);

					if (range == Range.EXACT) {
						if (canonicalMajorMinorFromServer.equals(annotationCanonicalMajorMinor)) {
							return ConditionOutcome.match();
						}
						else {
							return ConditionOutcome.noMatch(message.because("Version: " +
								annotationCanonicalMajorMinor +
								" does not match version from server: " +
								canonicalMajorMinorFromServer));
						}
					} else if (range == Range.EQUAL_OR_NEWER) {
						if(canonicalMajorMinorFromServer
							.compareTo(annotationCanonicalMajorMinor) >= 0) {
							return ConditionOutcome.match();
						}
						else {
							return ConditionOutcome.noMatch(message.because(
								"Server Version: " +
								canonicalMajorMinorFromServer + " is not greater or equal to " +
								annotationCanonicalMajorMinor));
						}
					} else {
						if(canonicalMajorMinorFromServer
							.compareTo(annotationCanonicalMajorMinor) < 0) {
							return ConditionOutcome.match();
						}
						else {
							return ConditionOutcome.noMatch(message.because(
								"Server Version: " +
								canonicalMajorMinorFromServer + " is not less than " +
								annotationCanonicalMajorMinor));
						}
					}
				}

				//should never happens
				return ConditionOutcome.noMatch(message.because("Invalid version specified"));
			}
			
			return ConditionOutcome.noMatch(message.because(
				"Unable to determine Kubernetes version - "
					+ "Invalid gitVersion from server: " + gitVersion
			));
		} catch (Exception e) {
			return ConditionOutcome.noMatch(
				message.because("Unable to determine Kubernetes version: " + e.getMessage()));
		}
	}

	// major and minor are pressured to be int and the method ensures
	// that both are have two digits ensuring that version 1.10 is
	// larger than 1.9
	private String canonicalMajorMinor(String major, String minor) {
		return String.format(
			"%02d.%02d",
			Integer.valueOf(major),
			Integer.valueOf(minor)
		);
	}

}
