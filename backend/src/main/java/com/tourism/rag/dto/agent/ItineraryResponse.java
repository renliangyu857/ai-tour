package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ItineraryResponse {

    private String itineraryId;             // UUID，用于 GET /api/agent/itinerary/{id}
    private String requestId;               // traceId，便于日志追踪
    private String cityCode;
    private String cityName;
    private String startDate;
    private String endDate;
    private int totalDays;
    private List<String> preferences;
    private String budget;
    private String transportMode;
    private String tripSummary;             // AI 生成的行程概述

    private List<DayPlan> days;

    /** 全程预算汇总 */
    private Map<String, String> totalBudget;

    /** 工具调用日志（可观测性） */
    private List<ToolCallLog> toolCallLogs;

    private String generatedAt;             // ISO-8601 生成时间
    private boolean hasRealWeatherData;
    private boolean hasRealPoiData;
    private boolean hasRealFoodData;
}
