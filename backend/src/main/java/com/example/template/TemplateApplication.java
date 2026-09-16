package com.example.template;

import com.example.template.cli.SetRoleCommand;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan // AppProperties
@EnableScheduling // the email queue worker
public class TemplateApplication {

    public static void main(String[] args) {
        // set-role <email> <role>: roles are granted out-of-band, there is no
        // HTTP endpoint for it. Deliberately does NOT load .env files and does
        // not start Spring — it reads DATABASE_URL directly, like the CLI
        // subcommand it replaces.
        if (args.length > 0 && "set-role".equals(args[0])) {
            System.exit(
                SetRoleCommand.run(Arrays.copyOfRange(args, 1, args.length))
            );
        }
        SpringApplication.run(TemplateApplication.class, args);
    }
}
