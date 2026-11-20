package com.travel2go.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.travel2go.agent.security.ApiKeyProperties;

// Also scan platform-security's service package so the shared QuoteTokenService
// (com.travel2go.backend.service) is picked up for G2 quote-token parity.
@SpringBootApplication(scanBasePackages = {"com.travel2go.agent", "com.travel2go.backend.service"})
@EnableFeignClients
@EnableConfigurationProperties(ApiKeyProperties.class)
public class AgentApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentApiApplication.class, args);
	}
}
