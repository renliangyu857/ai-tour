package com.tourism.rag.agent.multiagent.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.dto.agent.ItineraryRequest;
import com.tourism.rag.dto.agent.ItineraryResponse;
import com.tourism.rag.entity.ItineraryRecord;
import com.tourism.rag.repository.CityRepository;
import com.tourism.rag.repository.ItineraryRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Top-level orchestrator for multi-agent itinerary generation.
 *
 * <p>Orchestration flow:
 * <ol>
 *   <li>Build {@link ExecutionPlan} from agent dependency DAG</li>
 *   <li>Execute stages sequentially via {@link StageExecutor}</li>
 *   <li>Agents within each stage run in parallel on virtual threads</li>
 *   <li>Collect results via {@link ResultAggregator}</li>
 *   <li>Persist asynchronously</li>
 * </ol>
 *
 * <p>This service is used by both the non-streaming and streaming controllers.
 * The streaming variant additionally passes an event sink to publish real-time events.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AgentRegistry agentRegistry;
    private final StageExecutor stageExecutor;
    private final ResultAggregator resultAggregator;
    private final ObjectMapper objectMapper;
    private final ItineraryRecordRepository itineraryRepo;
    private final CityRepository cityRepository;

    // ---- All participating agent IDs in dependency order ----

    private static final List<String> PARTICIPATING_AGENTS = List.of(
            "weather-analysis",
            "poi-discovery",
            "route-optimization",
            "food-recommendation",
            "day-scheduling",
            "budget-planning",
            "narrative-generation",
            "safety-validation"
    );

    /**
     * Generate itinerary without streaming (one-shot response).
     */
    public ItineraryResponse generate(ItineraryRequest req, Long userId) {
        return executeOrchestration(req, userId, null);
    }

    /**
     * Generate itinerary with streaming events.
     *
     * @param eventSink consumer for AgentEvent (the SSE publisher)
     */
    public ItineraryResponse generateWithStreaming(ItineraryRequest req, Long userId,
                                                    Consumer<AgentEvent> eventSink) {
        return executeOrchestration(req, userId, eventSink);
    }

    private ItineraryResponse executeOrchestration(ItineraryRequest req, Long userId,
                                                    Consumer<AgentEvent> eventSink) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put("requestId", requestId);

        long orchestrationStart = System.currentTimeMillis();
        log.info("[Orchestrator] Starting multi-agent orchestration requestId={}, city={}",
                requestId, req.getCityCode());

        try {
            // 1. Validate & prepare
            validate(req);
            String cityName = resolveCityName(req.getCityCode());
            List<LocalDate> dates = buildDateRange(req.getStartDate(), req.getEndDate());

            // 2. Build context
            AgentContext ctx = new AgentContext(requestId, req, cityName, dates);

            // 3. Build execution plan (DAG → stages)
            ExecutionPlan plan = new ExecutionPlan(agentRegistry, PARTICIPATING_AGENTS);

            if (eventSink != null) {
                eventSink.accept(AgentEvent.orchestrationStarted(
                        requestId, cityName, plan.getTotalStages()));
            }

            // 4. Execute stages sequentially
            Map<String, AgentResult> allResults = new LinkedHashMap<>();
            for (ExecutionStage stage : plan.getStages()) {
                ctx.setCurrentStage(stage.getStageNumber());
                Map<String, AgentResult> stageResults = stageExecutor.execute(stage, ctx, eventSink);
                allResults.putAll(stageResults);
            }

            // 5. Check for critical failures
            verifyCriticalResults(allResults, ctx);

            // 6. Aggregate results into response
            String itineraryId = UUID.randomUUID().toString();
            ItineraryResponse response = resultAggregator.aggregate(ctx, itineraryId, allResults);

            // 7. Async persist
            saveAsync(itineraryId, req, response, userId);

            long totalDuration = System.currentTimeMillis() - orchestrationStart;
            log.info("[Orchestrator] Multi-agent orchestration complete itineraryId={}, duration={}ms, agents={}",
                    itineraryId, totalDuration, allResults.size());

            if (eventSink != null) {
                eventSink.accept(AgentEvent.finalResult(itineraryId, totalDuration));
            }

            return response;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Orchestrator] Orchestration failed requestId={}", requestId, e);
            throw new RuntimeException("Multi-agent itinerary generation failed: " + e.getMessage(), e);
        } finally {
            MDC.remove("requestId");
        }
    }

    /**
     * Verify that critical agents produced usable results.
     * Fails fast if weather + POI (stage 1) both failed completely.
     */
    private void verifyCriticalResults(Map<String, AgentResult> results, AgentContext ctx) {
        List<String> criticalErrors = new ArrayList<>();

        for (String agentId : List.of("weather-analysis", "poi-discovery")) {
            AgentResult r = results.get(agentId);
            if (r == null || r.getStatus() == AgentStatus.FAILED) {
                criticalErrors.add(agentId + " failed: " +
                        (r != null ? r.getErrorMessage() : "no result"));
            }
        }

        if (!criticalErrors.isEmpty()) {
            log.warn("[Orchestrator] Critical agent issues detected: {}", criticalErrors);
            // Don't throw — continue with fallback data. The aggregator will mark
            // hasRealWeatherData / hasRealPoiData as false so the frontend knows.
        }
    }

    // ---- Validation & helpers (mirrored from ItineraryAgentService) ----

    private void validate(ItineraryRequest req) {
        LocalDate start = LocalDate.parse(req.getStartDate(), DATE_FMT);
        LocalDate end = LocalDate.parse(req.getEndDate(), DATE_FMT);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        long days = start.until(end).getDays() + 1;
        if (days > 7) {
            throw new IllegalArgumentException("Maximum trip length is 7 days");
        }
    }

    private List<LocalDate> buildDateRange(String start, String end) {
        LocalDate s = LocalDate.parse(start, DATE_FMT);
        LocalDate e = LocalDate.parse(end, DATE_FMT);
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cur = s;
        while (!cur.isAfter(e)) {
            dates.add(cur);
            cur = cur.plusDays(1);
        }
        return dates;
    }

    private String resolveCityName(String cityCode) {
        return cityRepository.findByCode(cityCode)
                .map(c -> c.getNameCn())
                .orElseGet(() -> {
                    Map<String, String> names = Map.of(
                            "qingdao", "青岛", "beijing", "北京", "shanghai", "上海",
                            "xian", "西安", "chengdu", "成都", "guilin", "桂林",
                            "hangzhou", "杭州", "suzhou", "苏州");
                    return names.getOrDefault(cityCode.toLowerCase(),
                            cityCode.substring(0, 1).toUpperCase() + cityCode.substring(1));
                });
    }

    @Async
    public void saveAsync(String id, ItineraryRequest req, ItineraryResponse resp, Long userId) {
        try {
            itineraryRepo.save(ItineraryRecord.builder()
                    .id(id)
                    .cityCode(req.getCityCode())
                    .cityName(resp.getCityName())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .totalDays(resp.getTotalDays())
                    .responseJson(objectMapper.writeValueAsString(resp))
                    .requestJson(objectMapper.writeValueAsString(req))
                    .userId(userId)
                    .build());
        } catch (Exception e) {
            log.warn("[Orchestrator] Failed to persist itinerary id={}", id, e);
        }
    }
}
