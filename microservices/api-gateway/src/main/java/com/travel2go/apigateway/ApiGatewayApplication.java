package com.travel2go.apigateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class ApiGatewayApplication {

	private static final Logger logger = LoggerFactory.getLogger(ApiGatewayApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

	@Bean
	public GlobalFilter customGlobalFilter() {
		return (exchange, chain) -> {
			logger.info("Gateway received request: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI());
			return chain.filter(exchange).then(Mono.fromRunnable(() -> {
				logger.info("Gateway responded with status: {}", exchange.getResponse().getStatusCode());
			}));
		};
	}
}
