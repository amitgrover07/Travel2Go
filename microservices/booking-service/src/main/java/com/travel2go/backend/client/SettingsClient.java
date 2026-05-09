package com.travel2go.backend.client;

import com.travel2go.backend.model.GlobalSettings;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "identity-service", url = "${IDENTITY_SERVICE_URL:http://localhost:8081}")
public interface SettingsClient {

    @GetMapping("/api/settings/{id}")
    GlobalSettings getSettingsById(@PathVariable("id") String id);
}
