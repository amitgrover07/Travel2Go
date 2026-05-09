package com.travel2go.apigateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;

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

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // Be very explicit with origins
        corsConfig.setAllowedOrigins(Arrays.asList(
            "https://travel2go-495007.web.app", 
            "https://travel2go-495007.firebaseapp.com",
            "http://localhost:5173"
        ));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
