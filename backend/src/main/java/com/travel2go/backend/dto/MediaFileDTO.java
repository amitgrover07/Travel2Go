package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFileDTO {
    private String name;
    private String url;
    private Map<String, String> metadata;
    private Long size;
    private Long updatedAt;
}
