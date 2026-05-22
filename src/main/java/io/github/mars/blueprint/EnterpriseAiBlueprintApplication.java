package io.github.mars.blueprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("io.github.mars.blueprint")
public class EnterpriseAiBlueprintApplication {

	public static void main(String[] args) {
		SpringApplication.run(EnterpriseAiBlueprintApplication.class, args);
	}

}
