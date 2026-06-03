package com.tourism.rag.dto.multiagent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for multi-agent itinerary generation.
 * Same structure as ItineraryRequest — kept separate for API evolution flexibility.
 */
@Data
public class MultiAgentRequest {

    @NotBlank(message = "City code is required")
    private String cityCode;

    @NotBlank(message = "Start date is required")
    private String startDate;

    @NotBlank(message = "End date is required")
    private String endDate;

    private List<String> preferences;
    private String budget;          // low / medium / high
    private String transportMode;   // walking / driving / transit
    private Integer adults = 1;
    private Integer children = 0;
}
