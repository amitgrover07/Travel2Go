package com.travel2go.backend.dto;

import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailResponse {
    private Trip trip;
    private List<Leg> legs;
}
