package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTripRequest {
    private String title;
    private List<String> travellerIds;
    private String origin;
    private String destination;
    private Date startDate;
    private Date endDate;
}
