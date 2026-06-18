package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "configurator_categories")
public class ConfiguratorCategory {
    @DocumentId
    private String id;
    private String name;
    private String icon;
}
