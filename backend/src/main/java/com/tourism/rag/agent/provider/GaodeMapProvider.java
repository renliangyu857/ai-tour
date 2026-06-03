package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图 POI 搜索提供者。
 * 文档：https://lbs.amap.com/api/webservice/guide/api/search
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeMapProvider implements MapProvider {

    private static final String POI_URL =
            "https://restapi.amap.com/v3/place/text?keywords={kw}&city={city}&types={types}" +
            "&output=json&offset={limit}&key={key}&extensions=all";

    @Value("${agent.map.gaode.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MockMapProvider mockMapProvider;

    /** 高德 POI 类型代码：风景名胜=110000，博物馆=141200 */
    private static final String ATTRACTION_TYPES = "110000|141200|141300|140300|130300";

    @Override
    public List<PoiInfo> searchPOI(String cityCode, String cityName, List<String> keywords,
                                    List<String> preferences, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[Gaode] API key 未配置，跳过");
            return List.of();
        }
        try {
            String kw = keywords != null && !keywords.isEmpty() ? String.join("|", keywords) : "景点";
            String url = POI_URL
                    .replace("{kw}", java.net.URLEncoder.encode(kw, "UTF-8"))
                    .replace("{city}", cityName)
                    .replace("{types}", ATTRACTION_TYPES)
                    .replace("{limit}", String.valueOf(Math.min(maxResults, 25)))
                    .replace("{key}", apiKey);

            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);

            if (!"1".equals(root.path("status").asText())) {
                log.warn("[Gaode] POI 搜索返回非1：{}", root.path("info").asText());
                return List.of();
            }

            List<PoiInfo> result = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                String location = poi.path("location").asText("0,0");
                String[] parts = location.split(",");
                double lng = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);

                JsonNode biz = poi.path("biz_ext");
                double rating = biz.path("rating").asDouble(4.5);

                result.add(PoiInfo.builder()
                        .id(poi.path("id").asText())
                        .name(poi.path("name").asText())
                        .category(poi.path("type").asText())
                        .address(poi.path("address").asText())
                        .lat(lat).lng(lng)
                        .rating(rating)
                        .openingHours(poi.path("opentime_week").asText("请提前确认"))
                        .ticketPrice("请咨询景区")
                        .visitDurationMinutes(120)
                        .indoorVenue(false)
                        .description(poi.path("name").asText() + "，" + poi.path("address").asText())
                        .dataSource("gaode_api")
                        .build());
            }
            log.info("[Gaode] 获取到 {} 个 POI", result.size());
            return result;

        } catch (Exception e) {
            log.warn("[Gaode] POI 搜索失败：{}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public RouteInfo planRoute(List<PoiInfo> pois, double startLat, double startLng, String transportMode) {
        // 路线优化使用 Mock 的最近邻算法（高德路线 API 需要逐段调用，成本较高）
        return mockMapProvider.planRoute(pois, startLat, startLng, transportMode);
    }

    @Override
    public String providerName() {
        return "gaode_api";
    }
}
