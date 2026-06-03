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
 * 高德天气预报提供者（复用同一个 MAP_API_KEY，无需额外注册）。
 * 接口文档：https://lbs.amap.com/api/webservice/guide/api/weatherinfo
 * extensions=all 返回今天 + 未来 3 天，共 4 条逐日预报。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeWeatherProvider implements WeatherProvider {

    private static final String WEATHER_URL =
            "https://restapi.amap.com/v3/weather/weatherInfo?city={adcode}&extensions=all&output=JSON&key={key}";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${agent.map.gaode.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MockWeatherProvider mockWeatherProvider;

    /**
     * 城市 code → 高德 adcode（行政区划编码）。
     * 完整列表：https://lbs.amap.com/api/webservice/download
     */
    private static final Map<String, String> CITY_ADCODE = Map.ofEntries(
            Map.entry("qingdao",  "370200"),
            Map.entry("beijing",  "110100"),
            Map.entry("shanghai", "310100"),
            Map.entry("xian",     "610100"),
            Map.entry("chengdu",  "510100"),
            Map.entry("guilin",   "450300"),
            Map.entry("hangzhou", "330100"),
            Map.entry("suzhou",   "320500"),
            Map.entry("shenzhen", "440300"),
            Map.entry("guangzhou","440100")
    );

    /**
     * 高德天气文字描述 → 内部 condition 码。
     * 高德返回的是中文描述（如"晴""小雨""雷阵雨"），需手动映射。
     */
    private static String textToCondition(String text) {
        if (text == null) return "partly_cloudy";
        if (text.contains("雷"))                        return "thunderstorm";
        if (text.contains("暴雨") || text.contains("大雨")) return "heavy_rain";
        if (text.contains("中雨"))                      return "moderate_rain";
        if (text.contains("雨"))                        return "light_rain";
        if (text.contains("雪"))                        return "snow";
        if (text.contains("雾") || text.contains("霾")) return "fog";
        if (text.contains("阴"))                        return "cloudy";
        if (text.contains("多云"))                      return "partly_cloudy";
        if (text.contains("晴"))                        return "sunny";
        return "partly_cloudy";
    }

    @Override
    public List<WeatherInfo> getWeather(String cityCode, String cityName,
                                        String startDate, String endDate) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[GaodeWeather] API key 未配置，跳过");
            return List.of();
        }

        String adcode = CITY_ADCODE.get(cityCode.toLowerCase());
        if (adcode == null) {
            log.warn("[GaodeWeather] 未找到城市 adcode: {}，跳过", cityCode);
            return List.of();
        }

        try {
            String url = WEATHER_URL
                    .replace("{adcode}", adcode)
                    .replace("{key}", apiKey);
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);

            if (!"1".equals(root.path("status").asText())) {
                log.warn("[GaodeWeather] 接口返回非1：{}", root.path("info").asText());
                return List.of();
            }

            JsonNode casts = root.path("forecasts").get(0).path("casts");
            if (casts == null || casts.isEmpty()) return List.of();

            LocalDate start = LocalDate.parse(startDate, FMT);
            LocalDate end   = LocalDate.parse(endDate,   FMT);

            // 拿 Mock 数据做湿度/UV 兜底（高德预报不含这两项）
            List<WeatherInfo> mockBases = mockWeatherProvider
                    .getWeather(cityCode, cityName, startDate, endDate);
            Map<String, WeatherInfo> mockByDate = new java.util.HashMap<>();
            for (WeatherInfo w : mockBases) mockByDate.put(w.getDate(), w);

            List<WeatherInfo> result = new ArrayList<>();
            for (JsonNode cast : casts) {
                String dateStr = cast.path("date").asText();
                LocalDate date;
                try { date = LocalDate.parse(dateStr, FMT); }
                catch (Exception e) { continue; }
                if (date.isBefore(start) || date.isAfter(end)) continue;

                String conditionText = cast.path("dayweather").asText("晴");
                String condition     = textToCondition(conditionText);
                boolean outdoor      = !condition.contains("rain")
                        && !condition.contains("snow")
                        && !condition.contains("thunderstorm")
                        && !condition.contains("fog");

                int tempHigh = parseInt(cast.path("daytemp").asText("20"));
                int tempLow  = parseInt(cast.path("nighttemp").asText("12"));

                // 风力：高德返回如 "1-3"，取第一个数字
                String daypower = cast.path("daypower").asText("3");
                String windScale = daypower.contains("-") ? daypower.split("-")[0] : daypower;

                // 湿度/UV 使用 Mock 兜底
                WeatherInfo mock = mockByDate.get(dateStr);
                int humidity = mock != null ? mock.getHumidity() : 65;
                String uv    = mock != null ? mock.getUvIndex()  : "中等";

                result.add(WeatherInfo.builder()
                        .date(dateStr)
                        .condition(condition)
                        .conditionText(conditionText)
                        .tempHigh(tempHigh)
                        .tempLow(tempLow)
                        .windDir(cast.path("daywind").asText("东南"))
                        .windScale(windScale)
                        .humidity(humidity)
                        .uvIndex(uv)
                        .precipitation(condition.contains("rain") ? "有降水" : "无降水")
                        .outdoorFriendly(outdoor)
                        .dataSource("gaode_weather")
                        .build());
            }
            log.info("[GaodeWeather] 获取到 {} 天预报", result.size());
            return result;

        } catch (Exception e) {
            log.warn("[GaodeWeather] 天气查询失败：{}", e.getMessage());
            return List.of();
        }
    }

    private static int parseInt(String s) {
        try { return (int) Double.parseDouble(s.trim()); }
        catch (Exception e) { return 15; }
    }

    @Override
    public String providerName() {
        return "gaode_weather";
    }
}
