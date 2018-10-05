package org.springframework.cloud.kubernetes.config;

import static java.util.Arrays.asList;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.ABSTAIN;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.FOUND;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus.NOT_FOUND;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;

public class PropertySourceUtils {

	static final Function<String, Properties> KEY_VALUE_TO_PROPERTIES =
		s -> {
			Properties properties = new Properties();
			try {
				properties.load(new ByteArrayInputStream(s.getBytes()));
				return properties;
			}
			catch (IOException e) {
				throw new IllegalArgumentException();
		}
	};

	static final Function<Properties, Map<String, String>> PROPERTIES_TO_MAP =
		p -> p.entrySet()
				.stream()
				.collect(
					Collectors.toMap(
						e -> String.valueOf(e.getKey()),
						e -> String.valueOf(e.getValue())
					)
				);

	static Function<String, Properties> yamlParserGenerator(
		final String[] profiles) {
		return s -> {
			YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
			yamlFactory.setDocumentMatchers(properties -> {
				String profileProperty = properties.getProperty("spring.profiles");
				if (profileProperty != null && profileProperty.length() > 0) {
					return asList(profiles).contains(profileProperty) ? FOUND : NOT_FOUND;
				}
				else {
					return ABSTAIN;
				}
			});
			yamlFactory.setResources(new ByteArrayResource(s.getBytes()));
			return yamlFactory.getObject();
		};
	}
}
