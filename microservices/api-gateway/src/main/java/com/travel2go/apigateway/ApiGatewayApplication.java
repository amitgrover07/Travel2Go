package com.travel2go.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	@org.springframework.beans.factory.annotation.Value("${IDENTITY_SERVICE_URL:http://localhost:8081}")
	private String identityServiceUrl;

	@org.springframework.beans.factory.annotation.Value("${PACKAGE_SERVICE_URL:http://localhost:8082}")
	private String packageServiceUrl;

	@org.springframework.beans.factory.annotation.Value("${BOOKING_SERVICE_URL:http://localhost:8083}")
	private String bookingServiceUrl;

	@org.springframework.beans.factory.annotation.Value("${MEDIA_SERVICE_URL:http://localhost:8084}")
	private String mediaServiceUrl;

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
			.route("identity_service", r -> r.path("/api/auth/**", "/api/users/**", "/api/settings/**")
				.uri(identityServiceUrl))
			.route("package_service", r -> r.path("/api/packages/**")
				.uri(packageServiceUrl))
			.route("booking_service", r -> r.path("/api/bookings/**")
				.uri(bookingServiceUrl))
			.route("media_service", r -> r.path("/api/media/**")
				.uri(mediaServiceUrl))
			.build();
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
        corsConfig.setAllowedOrigins(Arrays.asList("http://localhost:5173", "https://travel2go-495007.web.app", "https://travel2go-495007.firebaseapp.com"));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
