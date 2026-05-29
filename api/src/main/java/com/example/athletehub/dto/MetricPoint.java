package com.example.athletehub.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One sample in a {@link MetricSeriesDto}. {@code at} is the source
 * evaluation's timestamp — the chart x-axis. Null values are filtered
 * out server-side so the wire payload only carries real datapoints.
 */
public record MetricPoint(
        OffsetDateTime at,
        BigDecimal value
) {}
