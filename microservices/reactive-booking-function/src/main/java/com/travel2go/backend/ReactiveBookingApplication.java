package com.travel2go.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
    "com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreAutoConfiguration",
    "com.google.cloud.spring.autoconfigure.firestore.FirestoreRepositoriesAutoConfiguration",
    "com.google.cloud.spring.autoconfigure.firestore.FirestoreTransactionManagerAutoConfiguration",
    "com.google.cloud.spring.autoconfigure.firestore.GcpFirestoreEmulatorAutoConfiguration",
    "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
    "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
    "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
    "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration",
    "org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration"
})
public class ReactiveBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveBookingApplication.class, args);
    }
}
