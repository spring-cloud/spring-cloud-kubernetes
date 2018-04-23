package org.springframework.cloud.kubernetes.examples;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class)
public class ApplicationTestIT {

    @Autowired
    private ApplicationContext context;

    /*
     * This test proves that the application can be loaded successful and that all @configurations and dependencies are there
     */
    @Test
    public void contextLoads() throws Exception {
        assertThat(context).isNotNull();
    }
}
