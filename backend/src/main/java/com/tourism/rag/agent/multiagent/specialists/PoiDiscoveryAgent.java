package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.agent.provider.MapProvider;
import com.tourism.rag.agent.provider.MockMapProvider;
import com.tourism.rag.dto.agent.PoiInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for POI (attraction) discovery and ranking.
 *
 * <p>Stage 1 — no dependencies. Runs in parallel with WeatherAnalysisAgent.</p>
 */
@Slf4j
@Component
public class PoiDiscoveryAgent extends Agent {

    private final MapProvider primaryProvider;
    private final MockMapProvider fallbackProvider;

    public PoiDiscoveryAgent(List<MapProvider> mapProviders, MockMapProvider mockMapProvider) {
        this.primaryProvider = mapProviders.stream()
                .filter(p -> !(p instanceof MockMapProvider))
                .findFirst()
                .orElse(mockMapProvider);
        this.fallbackProvider = mockMapProvider;
    }

    @Override
    public String agentId() {
        return "poi-discovery";
    }

    @Override
    public String displayName() {
        return "Attraction Discovery Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.poiDiscovery();
    }

    @Override
    public List<String> dependencies() {
        return List.of(); // Stage 1 — no dependencies
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String cityCode = ctx.getRequest().getCityCode();
        String cityName = ctx.getCityName();
        List<String> preferences = ctx.getRequest().getPreferences();

        List<PoiInfo> pois;
        boolean usedFallback = false;

        try {
            pois = primaryProvider.searchPOI(cityCode, cityName,
                    List.of("景点"), preferences, 12);
            if (pois.isEmpty()) {
                pois = fallbackProvider.searchPOI(cityCode, cityName,
                        List.of("景点"), preferences, 12);
                usedFallback = true;
            }
        } catch (Exception e) {
            log.warn("[PoiDiscoveryAgent] Primary provider failed, using fallback: {}", e.getMessage());
            pois = fallbackProvider.searchPOI(cityCode, cityName,
                    List.of("景点"), preferences, 12);
            usedFallback = true;
        }

        // Enrich POIs with preference-based ranking
        List<PoiInfo> ranked = rankByPreferences(pois, preferences);

        // Categorize into outdoor vs indoor
        List<PoiInfo> outdoor = ranked.stream().filter(p -> !p.isIndoorVenue()).toList();
        List<PoiInfo> indoor = ranked.stream().filter(PoiInfo::isIndoorVenue).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poiList", ranked);
        payload.put("outdoorPois", outdoor);
        payload.put("indoorPois", indoor);
        payload.put("totalCount", ranked.size());
        payload.put("cityCenter", fallbackProvider.getCityCenter(cityCode));

        // Also put city center in shared context for other agents
        double[] center = fallbackProvider.getCityCenter(cityCode);
        ctx.putState("cityCenterLat", center[0]);
        ctx.putState("cityCenterLng", center[1]);

        String summary = String.format("Discovered %d attractions in %s (%d outdoor, %d indoor) %s",
                ranked.size(), cityName, outdoor.size(), indoor.size(),
                usedFallback ? "[offline data]" : "[live data]");

        return usedFallback
                ? AgentResult.fallback(agentId(), "POI API unavailable; using curated offline data", payload)
                : AgentResult.success(agentId(), summary, payload);
    }

    /**
     * Rank POIs by how well they match user preferences.
     */
    private List<PoiInfo> rankByPreferences(List<PoiInfo> pois, List<String> preferences) {
        if (preferences == null || preferences.isEmpty()) return pois;

        List<PoiInfo> sorted = new ArrayList<>(pois);
        sorted.sort((a, b) -> {
            int scoreA = matchScore(a, preferences);
            int scoreB = matchScore(b, preferences);
            return Integer.compare(scoreB, scoreA); // descending
        });
        return sorted;
    }

    private int matchScore(PoiInfo poi, List<String> preferences) {
        int score = 0;
        if (poi.getTags() == null) return score;
        for (String tag : poi.getTags()) {
            for (String pref : preferences) {
                if (tag.toLowerCase().contains(pref.toLowerCase())) {
                    score += 2;
                }
            }
        }
        return score;
    }
}
