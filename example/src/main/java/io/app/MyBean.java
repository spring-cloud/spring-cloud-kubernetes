package io.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 *
 */
@Component
@ConfigurationProperties(prefix = "bean")
public class MyBean {

    private String message;

    @Scheduled(fixedDelay = 5000)
    public void hello() {
        System.out.println("The message is: " + message);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
