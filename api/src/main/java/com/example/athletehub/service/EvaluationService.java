package com.example.athletehub.service;

import com.example.athletehub.dto.CreateEvaluationRequest;
import com.example.athletehub.dto.EvaluationDto;
import com.example.athletehub.dto.EvaluationMeasurementDto;
import com.example.athletehub.dto.EvaluationMeasurementRequest;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.exception.BadRequestException;
import com.example.athletehub.exception.ResourceNotFoundException;
import com.example.athletehub.model.Evaluation;
import com.example.athletehub.model.EvaluationMeasurement;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.EvaluationMeasurementRepository;
import com.example.athletehub.repository.EvaluationRepository;
import com.example.athletehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AH-041 — save evaluation + body-fat computation. Three creation shapes:
 *
 * <ul>
 *   <li><b>weight-only</b>: {@code bfMethod} absent → row stored with
 *       {@code body_fat_pct} + {@code bf_method} both null; the schema
 *       XOR rule is satisfied.</li>
 *   <li><b>manual</b>: {@code bfMethod = "manual"} + {@code bodyFatPct}
 *       supplied → pass-through.</li>
 *   <li><b>computed</b>: {@code bfMethod ∈ {jackson_pollock_7, navy}} →
 *       service computes from the user's profile + measurements via
 *       {@link BodyFatCalculator}. {@code durnin} is reserved by the
 *       schema CHECK but rejected here as
 *       {@link MessageCode#BF_METHOD_NOT_SUPPORTED} until follow-up.</li>
 * </ul>
 *
 * <p>Measurements are stored regardless of method — the Evolution
 * time-series graphs always want the raw values, even when the
 * evaluation is weight-only.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final EvaluationMeasurementRepository measurementRepository;
    private final UserRepository userRepository;

    // ── create ───────────────────────────────────────────────────────────

    @Transactional
    public EvaluationDto create(Long userId, CreateEvaluationRequest request) {
        // Materialize measurements once — both the formulas and the row
        // INSERTs read from the same list.
        List<EvaluationMeasurementRequest> measurementReqs =
                request.getMeasurements() == null ? List.of() : request.getMeasurements();

        // Reject duplicate point_ids inside the same payload so we surface
        // the conflict as 400 rather than a 500 from the UNIQUE violation.
        Set<String> seenPoints = new HashSet<>();
        for (EvaluationMeasurementRequest m : measurementReqs) {
            if (!seenPoints.add(m.getPointId())) {
                throw new BadRequestException(MessageCode.VALIDATION_FAILED);
            }
        }

        // Resolve the body-fat shape.
        String bfMethod = request.getBfMethod();
        BigDecimal bodyFatPct = computeBodyFatPctIfRequested(userId, bfMethod, request, measurementReqs);

        // The body-fat XOR rule: both null or both set. The CHECK will
        // catch this too, but emit the friendly code first.
        if ((bodyFatPct == null) != (bfMethod == null)) {
            throw new BadRequestException(MessageCode.VALIDATION_FAILED);
        }

        Evaluation saved = evaluationRepository.save(Evaluation.builder()
                .userId(userId)
                .evaluatedAt(request.getEvaluatedAt())
                .weightKg(request.getWeightKg())
                .bodyFatPct(bodyFatPct)
                .bfMethod(bfMethod)
                .notes(request.getNotes())
                .source("self")
                .build());

        List<EvaluationMeasurement> persisted = new ArrayList<>();
        for (EvaluationMeasurementRequest m : measurementReqs) {
            persisted.add(measurementRepository.save(EvaluationMeasurement.builder()
                    .evaluationId(saved.getId())
                    .pointId(m.getPointId())
                    .kind(m.getKind())
                    .unit(m.getUnit())
                    .value(m.getValue())
                    .build()));
        }

        List<EvaluationMeasurementDto> measurementDtos = persisted.stream()
                .sorted((a, b) -> a.getPointId().compareTo(b.getPointId()))
                .map(EvaluationMeasurementDto::from)
                .toList();
        return EvaluationDto.from(saved, measurementDtos);
    }

    /**
     * Resolves the {@code body_fat_pct} value for the persisted row based on
     * the requested method. Returns null for a weight-only check-in.
     */
    private BigDecimal computeBodyFatPctIfRequested(Long userId, String bfMethod,
                                                    CreateEvaluationRequest request,
                                                    List<EvaluationMeasurementRequest> measurements) {
        if (bfMethod == null) return null;

        switch (bfMethod) {
            case "manual" -> {
                if (request.getBodyFatPct() == null) {
                    throw new BadRequestException(MessageCode.BF_MANUAL_REQUIRES_PCT);
                }
                return request.getBodyFatPct();
            }
            case "durnin" -> {
                // Schema accepts it; service hasn't implemented it yet.
                throw new BadRequestException(MessageCode.BF_METHOD_NOT_SUPPORTED);
            }
            case "jackson_pollock_7" -> {
                User user = loadUser(userId);
                requireUserField(user.getSex(), "sex");
                requireUserField(user.getAge(), "age");
                Map<String, BigDecimal> skinfolds = collectByKind(measurements, "skinfold", "mm");
                if (!skinfolds.keySet().containsAll(BodyFatCalculator.JP7_REQUIRED_POINTS)) {
                    throw new BadRequestException(MessageCode.BF_MISSING_MEASUREMENTS);
                }
                return BodyFatCalculator.jacksonPollock7(skinfolds, user.getSex(), user.getAge());
            }
            case "navy" -> {
                User user = loadUser(userId);
                requireUserField(user.getSex(), "sex");
                requireUserField(user.getHeightCm(), "heightCm");
                Map<String, BigDecimal> circumferences = collectByKind(measurements, "circumference", "cm");
                if (!circumferences.keySet().containsAll(BodyFatCalculator.navyRequiredPoints(user.getSex()))) {
                    throw new BadRequestException(MessageCode.BF_MISSING_MEASUREMENTS);
                }
                return BodyFatCalculator.navy(circumferences, user.getSex(), user.getHeightCm());
            }
            default -> throw new BadRequestException(MessageCode.BF_METHOD_NOT_SUPPORTED);
        }
    }

    private static Map<String, BigDecimal> collectByKind(List<EvaluationMeasurementRequest> measurements,
                                                         String kind, String unit) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (EvaluationMeasurementRequest m : measurements) {
            if (kind.equals(m.getKind()) && unit.equals(m.getUnit())) {
                out.put(m.getPointId(), m.getValue());
            }
        }
        return out;
    }

    private static void requireUserField(Object value, String field) {
        if (value == null) {
            throw new BadRequestException(MessageCode.BF_MISSING_USER_FIELD);
        }
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.RESOURCE_NOT_FOUND));
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EvaluationDto getById(Long userId, Long evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(MessageCode.EVALUATION_NOT_FOUND));
        List<EvaluationMeasurementDto> measurements =
                measurementRepository.findByEvaluationIdOrderByPointIdAsc(evaluationId).stream()
                        .map(EvaluationMeasurementDto::from)
                        .toList();
        return EvaluationDto.from(evaluation, measurements);
    }
}
