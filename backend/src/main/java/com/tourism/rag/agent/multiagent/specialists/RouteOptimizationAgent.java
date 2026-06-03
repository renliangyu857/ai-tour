package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.agent.provider.MapProvider;
import com.tourism.rag.agent.provider.MockMapProvider;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for multi-stop route optimization.
 *
 * <p>Stage 2 — depends on PoiDiscoveryAgent and WeatherAnalysisAgent.</p>
 */
@Slf4j
@Component
public class RouteOptimizationAgent extends Agent {

    private final MockMapProvider mockMap;

    public RouteOptimizationAgent(MockMapProvider mockMap) {
        this.mockMap = mockMap;
    }

    @Override
    public String agentId() {
        return "route-optimization";
    }

    @Override
    public String displayName() {
        return "Route Optimization Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.routeOptimization();
    }

    @Override
    public List<String> dependencies() {
        return List.of("poi-discovery", "weather-analysis");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String transportMode = ctx.getRequest().getTransportMode();
        if (transportMode == null) transportMode = "transit";

        @SuppressWarnings("unchecked")
        List<PoiInfo> allPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("poiList");
        @SuppressWarnings("unchecked")
        List<PoiInfo> outdoorPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("outdoorPois");
        @SuppressWarnings("unchecked")
        List<PoiInfo> indoorPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("indoorPois");

        double cityLat = ctx.getState("cityCenterLat") != null
                ? (double) ctx.getState("cityCenterLat") : 36.06;
        double cityLng = ctx.getState("cityCenterLng") != null
                ? (double) ctx.getState("cityCenterLng") : 120.38;

        int totalDays = ctx.getTotalDays();
        int poisPerDay = Math.max(2, Math.min(4,
                allPois.size() / Math.max(1, totalDays)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poisPerDay", poisPerDay);

        for (int day = 1; day <= totalDays; day++) {
            int startIdx = ((day - 1) * poisPerDay) % Math.max(1, allPois.size());
            List<PoiInfo> dayPois = new ArrayList<>();
            for (int j = 0; j < poisPerDay; j++) {
                dayPois.add(allPois.get((startIdx + j) % allPois.size()));
            }

            // Main route (outdoor-focused)
            List<PoiInfo> mainRoutePois = new ArrayList<>(dayPois);
            RouteInfo mainRoute = mockMap.planRoute(mainRoutePois, cityLat, cityLng, transportMode);
            payload.put("day" + day + "_route", mainRoute);

            // Alternate route (indoor-focused)
            List<PoiInfo> altRoutePois = indoorPois.isEmpty()
                    ? new ArrayList<>(dayPois)
                    : indoorPois.stream().limit(poisPerDay).toList();
            RouteInfo altRoute = mockMap.planRoute(altRoutePois, cityLat, cityLng, transportMode);
            payload.put("day" + day + "_altRoute", altRoute);
        }

        String summary = String.format("Optimized %d-day route plan (%d POIs/day, %s mode)",
                totalDays, poisPerDay, transportMode);

        return AgentResult.success(agentId(), summary, payload);
    }
}
