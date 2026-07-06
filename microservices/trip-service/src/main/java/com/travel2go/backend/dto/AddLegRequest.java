package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddLegRequest {
    private String type;
    private Long pricePaise;
    private String quoteToken;
    private Date startAt;
    private Date endAt;
    private Map<String, Object> metadata;
}
