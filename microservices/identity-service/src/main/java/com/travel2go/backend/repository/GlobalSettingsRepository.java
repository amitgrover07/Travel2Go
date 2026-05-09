package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.GlobalSettings;

public interface GlobalSettingsRepository extends FirestoreReactiveRepository<GlobalSettings> {
}
