package com.travel2go.backend.repository;

import com.travel2go.backend.model.HolidayPackage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HolidayPackageRepository extends MongoRepository<HolidayPackage, String> {
    List<HolidayPackage> findByStatus(String status);
}
