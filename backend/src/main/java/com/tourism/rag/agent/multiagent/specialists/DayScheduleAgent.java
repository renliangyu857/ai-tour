package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.dto.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for building daily time-slot activity schedules.
 *
 * <p>Stage 3 — depends on RouteOptimizationAgent, FoodRecommendationAgent, and WeatherAnalysisAgent.</p>
 */
@Slf4j
@Component
public class DayScheduleAgent extends Agent {

    @Override
    public String agentId() {
        return "day-scheduling";
    }

    @Override
    public String displayName() {
        return "Time Management Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.dayScheduling();
    }

    @Override
    public List<String> dependencies() {
        return List.of("route-optimization", "food-recommendation", "weather-analysis");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        int totalDays = ctx.getTotalDays();
        String transportMode = ctx.getRequest().getTransportMode();
        if (transportMode == null) transportMode = "transit";

        @SuppressWarnings("unchecked")
        List<WeatherInfo> weatherList = (List<WeatherInfo>)
                ctx.getResult("weather-analysis").getPayload().get("weatherList");

        Map<String, Object> payload = new LinkedHashMap<>();

        for (int day = 1; day <= totalDays; day++) {
            // Get route for this day
            RouteInfo route = (RouteInfo) ctx.getResult("route-optimization")
                    .getPayload().get("day" + day + "_route");
            RouteInfo altRoute = (RouteInfo) ctx.getResult("route-optimization")
                    .getPayload().get("day" + day + "_altRoute");

            // Get weather for this day
            WeatherInfo weather = weatherList != null && weatherList.size() >= day
                    ? weatherList.get(day - 1)
                    : createDefaultWeather();

            // Build main schedule
            List<TimeSlotActivity> mainActivities = buildDailySchedule(
                    route, weather, transportMode, true);
            payload.put("day" + day + "_mainActivities", mainActivities);

            // Build alternate schedule (for bad weather)
            List<TimeSlotActivity> altActivities = buildDailySchedule(
                    altRoute, weather, transportMode, false);
            payload.put("day" + day + "_alternateActivities", altActivities);
        }

        String summary = String.format("Scheduled %d days with time-slot activities", totalDays);
        return AgentResult.success(agentId(), summary, payload);
    }

    /**
     * Build a full-day schedule: morning attractions → lunch → afternoon attractions
     * → evening free time → dinner → nighttime free time.
     */
    private List<TimeSlotActivity> buildDailySchedule(RouteInfo route, WeatherInfo weather,
                                                       String transport, boolean isMain) {
        List<TimeSlotActivity> activities = new ArrayList<>();
        List<PoiInfo> pois = route != null ? route.getOptimizedPois() : List.of();
        if (pois.isEmpty()) return activities;

        // Split POIs into morning and afternoon groups
        int half = (pois.size() + 1) / 2;
        List<PoiInfo> morningPois = new ArrayList<>(pois.subList(0, half));
        List<PoiInfo> afternoonPois = pois.size() > half
                ? new ArrayList<>(pois.subList(half, pois.size()))
                : List.of();

        int h = 9, m = 0;

        // Morning session (from 09:00)
        for (int i = 0; i < morningPois.size(); i++) {
            PoiInfo poi = morningPois.get(i);
            int travelMin = (i > 0 && route.getLegs() != null && route.getLegs().size() > i)
                    ? route.getLegs().get(i).getDurationMinutes() : 0;
            if (i > 0) { m += travelMin; h += m / 60; m %= 60; }

            String from = hm(h, m);
            int visitMin = Math.min(poi.getVisitDurationMinutes(), 150);
            m += visitMin; h += m / 60; m %= 60;
            if (h >= 12) { h = 12; m = 0; }
            String to = hm(h, m);

            activities.add(TimeSlotActivity.builder()
                    .timeSlot(from + "-" + to)
                    .activity(poi.getName())
                    .type("attraction")
                    .poi(poi)
                    .durationMinutes(visitMin)
                    .transportFromPrev(i == 0 ? "从酒店出发" : transportSuggestion(travelMin, transport))
                    .transportMinutes(travelMin)
                    .estimatedCost(parseTicketCost(poi.getTicketPrice()))
                    .notes(buildNote(poi, weather))
                    .build());
        }

        // Lunch break (12:00-13:00)
        activities.add(TimeSlotActivity.builder()
                .timeSlot("12:00-13:00")
                .activity("午餐时间")
                .type("food")
                .durationMinutes(60)
                .estimatedCost(0)
                .notes("推荐就近品尝当地特色美食")
                .build());

        // Afternoon session (from 13:00)
        h = 13; m = 0;
        for (int i = 0; i < afternoonPois.size(); i++) {
            PoiInfo poi = afternoonPois.get(i);
            int absLeg = half + i;
            int travelMin = (i > 0 && route.getLegs() != null && route.getLegs().size() > absLeg)
                    ? route.getLegs().get(absLeg).getDurationMinutes() : 0;
            if (i > 0) { m += travelMin; h += m / 60; m %= 60; }

            String from = hm(h, m);
            int visitMin = poi.getVisitDurationMinutes();
            m += visitMin; h += m / 60; m %= 60;
            if (h >= 18) { h = 17; m = 30; }
            String to = hm(h, m);

            activities.add(TimeSlotActivity.builder()
                    .timeSlot(from + "-" + to)
                    .activity(poi.getName())
                    .type("attraction")
                    .poi(poi)
                    .durationMinutes(visitMin)
                    .transportFromPrev(i == 0 ? "午餐后出发" : transportSuggestion(travelMin, transport))
                    .transportMinutes(travelMin)
                    .estimatedCost(parseTicketCost(poi.getTicketPrice()))
                    .notes(buildNote(poi, weather))
                    .build());
        }

        // Evening free time
        if (h < 17) {
            activities.add(TimeSlotActivity.builder()
                    .timeSlot(hm(h, m) + "-17:30")
                    .activity("自由探索 / 休闲购物")
                    .type("rest")
                    .durationMinutes(0)
                    .estimatedCost(0)
                    .notes("自由漫步周边街区，感受当地市井风情")
                    .build());
        }

        // Dinner (18:00-19:30)
        activities.add(TimeSlotActivity.builder()
                .timeSlot("18:00-19:30")
                .activity("晚餐时间")
                .type("food")
                .durationMinutes(90)
                .estimatedCost(0)
                .notes("结束今日游览，享用晚餐")
                .build());

        // Nighttime (19:30-21:00)
        activities.add(TimeSlotActivity.builder()
                .timeSlot("19:30-21:00")
                .activity("夜间自由时光")
                .type("rest")
                .durationMinutes(90)
                .estimatedCost(0)
                .notes("漫步夜市、欣赏夜景，或提前返回住所休息")
                .build());

        return activities;
    }

    private WeatherInfo createDefaultWeather() {
        return WeatherInfo.builder()
                .date("2024-01-01")
                .condition("sunny")
                .conditionText("晴")
                .tempHigh(25).tempLow(15)
                .outdoorFriendly(true)
                .dataSource("默认")
                .build();
    }

    private static String hm(int hour, int min) {
        return String.format("%02d:%02d", Math.min(hour, 22), min % 60);
    }

    private static String transportSuggestion(int minutes, String mode) {
        if (minutes <= 10) return "步行";
        if ("driving".equals(mode)) return "打车/自驾";
        if (minutes <= 20) return "公交";
        return "公交/地铁";
    }

    private static double parseTicketCost(String ticketPrice) {
        if (ticketPrice == null || ticketPrice.contains("免费")) return 0;
        try {
            return Double.parseDouble(ticketPrice.replaceAll("[^\\d.]", "").split("\\.")[0]);
        } catch (Exception e) { return 50; }
    }

    private static String buildNote(PoiInfo poi, WeatherInfo weather) {
        List<String> notes = new ArrayList<>();
        if (poi.getTicketPrice() != null && !poi.getTicketPrice().contains("免费")) {
            notes.add("需购票：" + poi.getTicketPrice());
        }
        if (!weather.isOutdoorFriendly() && !poi.isIndoorVenue()) {
            notes.add("天气原因建议携带雨具");
        }
        if (poi.getOpeningHours() != null && !poi.getOpeningHours().contains("全天")) {
            notes.add("开放时间：" + poi.getOpeningHours());
        }
        return String.join("；", notes);
    }
}
