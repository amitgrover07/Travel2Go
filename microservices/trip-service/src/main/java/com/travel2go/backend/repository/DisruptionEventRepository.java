package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.DisruptionEvent;
import org.springframework.stereotype.Repository;

@Repository
public interface DisruptionEventRepository extends FirestoreReactiveRepository<DisruptionEvent> {
}
