package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.FoodRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 高德地图美食 POI 搜索提供者。
 * 使用高德 POI 周边搜索（around）接口，类型 050000=餐饮。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeFoodProvider implements FoodProvider {

    private static final String AROUND_URL =
            "https://restapi.amap.com/v3/place/around?location={lng},{lat}&radius=3000" +
            "&types=050000&output=json&offset={limit}&sortrule=rating&key={key}&extensions=all";

    @Value("${agent.map.gaode.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<FoodRecommendation> recommendFood(String cityCode, String cityName,
                                                   double lat, double lng,
                                                   String mealType, List<String> preferences,
                                                   double minRating, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[GaodeFood] API key 未配置，跳过");
            return List.of();
        }
        try {
            String url = AROUND_URL
                    .replace("{lng}", String.valueOf(lng))
                    .replace("{lat}", String.valueOf(lat))
                    .replace("{limit}", String.valueOf(Math.min(maxResults * 3, 25)))
                    .replace("{key}", apiKey);

            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);

            if (!"1".equals(root.path("status").asText())) {
                log.warn("[GaodeFood] 美食搜索返回非1：{}", root.path("info").asText());
                return List.of();
            }

            List<FoodRecommendation> result = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                JsonNode biz    = poi.path("biz_ext");
                double rating   = biz.path("rating").asDouble(4.0);
                if (rating < minRating) continue;

                String location = poi.path("location").asText("0,0");
                String[] parts  = location.split(",");
                double pLng = Double.parseDouble(parts[0]);
                double pLat = Double.parseDouble(parts[1]);
                double dist = MockMapProvider.haversineKm(lat, lng, pLat, pLng);

                String avgCost = biz.path("cost").asText("未知");
                String priceRange = avgCost.equals("未知") ? "价格待询" : avgCost + "元/人";

                result.add(FoodRecommendation.builder()
                        .name(poi.path("name").asText())
                        .category(poi.path("type").asText())
                        .rating(rating)
                        .priceRange(priceRange)
                        .distanceKm(Math.round(dist * 10.0) / 10.0)
                        .address(poi.path("address").asText())
                        .businessStatus(biz.path("open_time").asText("营业中"))
                        .openingHours(poi.path("opentime_week").asText("请提前确认"))
                        .phone(poi.path("tel").asText(""))
                        .mealType(mealType)
                        .lat(pLat).lng(pLng)
                        .recommendReason("评分 " + rating + "，距景点约 " + dist + " 公里")
                        .dataSource("gaode_api")
                        .build());
            }

            return result.stream()
                    .sorted(Comparator.comparingDouble(FoodRecommendation::getDistanceKm))
                    .limit(maxResults)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("[GaodeFood] 美食搜索失败：{}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public String providerName() {
        return "gaode_api";
    }
}
