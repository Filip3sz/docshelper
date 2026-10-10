package code.filipesz.docshelper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DocshelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocshelperApplication.class, args);
    }
}