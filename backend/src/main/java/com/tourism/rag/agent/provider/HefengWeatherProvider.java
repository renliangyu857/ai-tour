package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.WeatherInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 和风天气（QWeather）API 提供者。
 * 文档：https://dev.qweather.com/docs/api/weather/weather-daily-forecast/
 * 免费版支持 3 天，付费版支持 7/15/30 天。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HefengWeatherProvider implements WeatherProvider {

    private static final String GEO_URL    = "https://geoapi.qweather.com/v2/city/lookup?location={city}&key={key}&lang=zh";
    private static final String WEATHER_URL = "https://devapi.qweather.com/v7/weather/7d?location={loc}&key={key}&lang=zh&unit=m";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${agent.weather.hefeng.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MockWeatherProvider mockWeatherProvider;

    /** 图标代码 -> condition 字符串映射（仅常见类型） */
    private static final Map<String, String> ICON_TO_CONDITION = Map.ofEntries(
            Map.entry("100", "sunny"),
            Map.entry("101", "partly_cloudy"),
            Map.entry("102", "partly_cloudy"),
            Map.entry("103", "partly_cloudy"),
            Map.entry("104", "cloudy"),
            Map.entry("150", "sunny"),       // 晴（夜）
            Map.entry("300", "light_rain"),
            Map.entry("301", "light_rain"),
            Map.entry("302", "thunderstorm"),
            Map.entry("303", "thunderstorm"),
            Map.entry("304", "thunderstorm"),
            Map.entry("305", "light_rain"),
            Map.entry("306", "moderate_rain"),
            Map.entry("307", "heavy_rain"),
            Map.entry("400", "snow"),
            Map.entry("401", "snow"),
            Map.entry("500", "fog"),
            Map.entry("501", "fog")
    );

    @Override
    public List<WeatherInfo> getWeather(String cityCode, String cityName, String startDate, String endDate) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[Hefeng] API key 未配置，跳过");
            return List.of();
        }
        try {
            // Step1: 城市名 → location ID
            String locationId = lookupLocationId(cityName);
            if (locationId == null) return List.of();

            // Step2: 获取 7 日天气
            String url = WEATHER_URL
                    .replace("{loc}", locationId)
                    .replace("{key}", apiKey);
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);

            if (!"200".equals(root.path("code").asText())) {
                log.warn("[Hefeng] API 返回非200：{}", root.path("code").asText());
                return List.of();
            }

            LocalDate start = LocalDate.parse(startDate, FMT);
            LocalDate end   = LocalDate.parse(endDate,   FMT);
            List<WeatherInfo> result = new ArrayList<>();

            for (JsonNode daily : root.path("daily")) {
                String fxDate = daily.path("fxDate").asText();
                LocalDate date = LocalDate.parse(fxDate, FMT);
                if (date.isBefore(start) || date.isAfter(end)) continue;

                String iconDay   = daily.path("iconDay").asText("100");
                String condition = ICON_TO_CONDITION.getOrDefault(iconDay, "partly_cloudy");
                boolean outdoor  = !condition.contains("rain") && !condition.contains("snow")
                        && !condition.contains("thunderstorm") && !condition.contains("fog");

                result.add(WeatherInfo.builder()
                        .date(fxDate)
                        .condition(condition)
                        .conditionText(daily.path("textDay").asText("晴"))
                        .tempHigh(daily.path("tempMax").asInt(20))
                        .tempLow(daily.path("tempMin").asInt(12))
                        .windDir(daily.path("windDirDay").asText("东南"))
                        .windScale(daily.path("windScaleDay").asText("3"))
                        .humidity(daily.path("humidity").asInt(65))
                        .uvIndex(daily.path("uvIndex").asText("中等"))
                        .precipitation(daily.path("precip").asText("0") + "mm")
                        .outdoorFriendly(outdoor)
                        .dataSource("hefeng_api")
                        .build());
            }
            log.info("[Hefeng] 获取到 {} 天天气数据", result.size());
            return result;

        } catch (Exception e) {
            log.warn("[Hefeng] 获取天气失败：{}", e.getMessage());
            return List.of();
        }
    }

    private String lookupLocationId(String cityName) {
        try {
            String url = GEO_URL.replace("{city}", cityName).replace("{key}", apiKey);
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            if ("200".equals(root.path("code").asText())) {
                return root.path("location").get(0).path("id").asText();
            }
        } catch (Exception e) {
            log.warn("[Hefeng] 城市查询失败：{}", e.getMessage());
        }
        return null;
    }

    @Override
    public String providerName() {
        return "hefeng_api";
    }
}
