package com.travel2go.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.travel2go.agent.security.ApiKeyProperties;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(ApiKeyProperties.class)
public class AgentApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentApiApplication.class, args);
	}
}
