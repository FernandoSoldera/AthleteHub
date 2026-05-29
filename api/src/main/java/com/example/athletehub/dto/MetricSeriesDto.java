package com.example.athletehub.dto;

import java.util.List;

/**
 * Time-series payload for the Body charts. {@code metric} echoes what the
 * client asked for; {@code unit} is derived server-side ({@code "kg"} for
 * weight, {@code "%"} for body-fat, {@code "cm"} / {@code "mm"} for
 * circumferences / skinfolds based on the stored measurement unit).
 *
 * <p>Points come ordered oldest → newest so the client can render straight
 * into a line chart without re-sorting.
 */
public record MetricSeriesDto(
        String metric,
        String range,
        String unit,
        List<MetricPoint> points
) {}
