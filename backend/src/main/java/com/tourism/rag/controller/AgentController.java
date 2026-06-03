package com.tourism.rag.controller;

import com.tourism.rag.dto.agent.ItineraryRequest;
import com.tourism.rag.dto.agent.ItineraryResponse;
import com.tourism.rag.security.AuthUser;
import com.tourism.rag.service.ItineraryAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能行程 Agent REST 端点。
 * 不依赖 /api/chat/** 链路，完全独立。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ItineraryAgentService agentService;

    /**
     * 生成智能行程。
     * 支持匿名访问（不保存 userId）和已登录访问（关联 userId）。
     */
    @PostMapping("/itinerary")
    public ResponseEntity<?> generateItinerary(
            @Valid @RequestBody ItineraryRequest request,
            @AuthenticationPrincipal AuthUser currentUser) {

        Long userId = currentUser != null ? currentUser.getId() : null;
        log.info("[AgentController] generateItinerary city={}, userId={}",
                request.getCityCode(), userId);

        ItineraryResponse response = agentService.generate(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 根据 ID 查询历史行程（已保存）。
     */
    @GetMapping("/itinerary/{id}")
    public ResponseEntity<?> getItinerary(@PathVariable String id) {
        return agentService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 健康检查 + Agent 配置摘要。
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "agent", "itinerary",
                "version", "2.0",
                "mode", "single-agent",  // legacy mode; multi-agent at /api/multi-agent
                "multiAgentAvailable", true,
                "endpoints", Map.of(
                        "generate",     "POST /api/agent/itinerary",
                        "getById",      "GET  /api/agent/itinerary/{id}",
                        "status",       "GET  /api/agent/status",
                        "multiAgent",   "POST /api/multi-agent/itinerary",
                        "multiStream",  "POST /api/multi-agent/itinerary/stream",
                        "multiAgents",  "GET  /api/multi-agent/agents",
                        "multiStatus",  "GET  /api/multi-agent/status"
                )
        ));
    }
}
