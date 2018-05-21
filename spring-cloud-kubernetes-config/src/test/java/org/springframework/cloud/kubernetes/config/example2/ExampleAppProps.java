package org.springframework.cloud.kubernetes.config.example2;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bean")
public class ExampleAppProps {

	private String commonMessage;
	private String message1;
	private String message2;
	private String message3;

	public String getCommonMessage() {
		return commonMessage;
	}

	public void setCommonMessage(String commonMessage) {
		this.commonMessage = commonMessage;
	}

	public String getMessage1() {
		return message1;
	}

	public void setMessage1(String message1) {
		this.message1 = message1;
	}

	public String getMessage2() {
		return message2;
	}

	public void setMessage2(String message2) {
		this.message2 = message2;
	}

	public String getMessage3() {
		return message3;
	}

	public void setMessage3(String message3) {
		this.message3 = message3;
	}
}
