package com.travel2go.backend.client;

import com.travel2go.backend.model.CustomPackage;
import com.travel2go.backend.model.HolidayPackage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "package-service", url = "${PACKAGE_SERVICE_URL:http://localhost:8082}")
public interface PackageClient {

    @GetMapping("/api/packages/{id}")
    HolidayPackage getPackageById(@PathVariable("id") String id);

    @GetMapping("/api/custom-packages/{id}")
    CustomPackage getCustomPackageById(@PathVariable("id") String id);
}
