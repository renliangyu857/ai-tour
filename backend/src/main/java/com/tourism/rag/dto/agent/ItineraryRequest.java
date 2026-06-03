package com.tourism.rag.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ItineraryRequest {

    @NotBlank(message = "cityCode 不能为空")
    private String cityCode;

    @NotBlank(message = "startDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String startDate;

    @NotBlank(message = "endDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String endDate;

    /**
     * 出行偏好：family / couple / food / photography / budget / culture / adventure
     */
    @Size(max = 5, message = "偏好最多选 5 项")
    private List<String> preferences;

    /**
     * 预算档位：low(< 300/天) / medium(300-600/天) / high(> 600/天)
     */
    private String budget = "medium";

    /**
     * 出行方式：walking / driving / transit
     */
    private String transportMode = "transit";

    private Integer adults = 2;
    private Integer children = 0;
}
