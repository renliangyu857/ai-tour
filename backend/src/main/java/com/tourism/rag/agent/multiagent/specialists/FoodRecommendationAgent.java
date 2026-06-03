package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.agent.provider.FoodProvider;
import com.tourism.rag.agent.provider.MockFoodProvider;
import com.tourism.rag.dto.agent.FoodRecommendation;
import com.tourism.rag.dto.agent.PoiInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for culinary recommendations near daily POIs.
 *
 * <p>Stage 2 — depends on PoiDiscoveryAgent.</p>
 */
@Slf4j
@Component
public class FoodRecommendationAgent extends Agent {

    private final FoodProvider primaryProvider;
    private final MockFoodProvider fallbackProvider;

    public FoodRecommendationAgent(List<FoodProvider> foodProviders,
                                    MockFoodProvider mockFoodProvider) {
        this.primaryProvider = foodProviders.stream()
                .filter(p -> !(p instanceof MockFoodProvider))
                .findFirst()
                .orElse(mockFoodProvider);
        this.fallbackProvider = mockFoodProvider;
    }

    @Override
    public String agentId() {
        return "food-recommendation";
    }

    @Override
    public String displayName() {
        return "Culinary Discovery Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.foodRecommendation();
    }

    @Override
    public List<String> dependencies() {
        return List.of("poi-discovery");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String cityCode = ctx.getRequest().getCityCode();
        String cityName = ctx.getCityName();
        List<String> preferences = ctx.getRequest().getPreferences();

        @SuppressWarnings("unchecked")
        List<PoiInfo> allPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("poiList");

        double minRating = 4.0;
        int maxResults = 5;
        int totalDays = ctx.getTotalDays();
        boolean usedFallback = false;

        Map<String, Object> payload = new LinkedHashMap<>();

        for (int day = 1; day <= totalDays; day++) {
            // Use the first POI of each day as the reference location
            int poiIdx = (day - 1) % Math.max(1, allPois.size());
            PoiInfo refPoi = allPois.get(poiIdx);
            double refLat = refPoi.getLat();
            double refLng = refPoi.getLng();

            List<FoodRecommendation> allMeals = new ArrayList<>();

            try {
                List<FoodRecommendation> lunch = primaryProvider.recommendFood(
                        cityCode, cityName, refLat, refLng, "lunch",
                        preferences, minRating, maxResults);
                if (!lunch.isEmpty()) {
                    allMeals.addAll(lunch);
                } else {
                    allMeals.addAll(fallbackProvider.recommendFood(
                            cityCode, cityName, refLat, refLng, "lunch",
                            preferences, minRating, maxResults));
                    usedFallback = true;
                }
            } catch (Exception e) {
                log.warn("[FoodRecommendationAgent] Primary failed for day {}: {}", day, e.getMessage());
                allMeals.addAll(fallbackProvider.recommendFood(
                        cityCode, cityName, refLat, refLng, "lunch",
                        preferences, minRating, maxResults));
                usedFallback = true;
            }

            payload.put("day" + day + "_foods", allMeals);
        }

        String summary = String.format("Recommended dining options for %d days in %s %s",
                totalDays, cityName, usedFallback ? "[offline data]" : "[live data]");

        return usedFallback
                ? AgentResult.fallback(agentId(), "Food API unavailable; using curated offline data", payload)
                : AgentResult.success(agentId(), summary, payload);
    }
}
