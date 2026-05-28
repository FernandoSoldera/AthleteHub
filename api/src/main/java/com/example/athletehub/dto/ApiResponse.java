package com.example.athletehub.dto;

import com.example.athletehub.enums.MessageCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard envelope for simple success/error responses. Endpoints that return
 * data use a dedicated *Response DTO instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse {

    private Boolean success;
    private String message;
    private MessageCode code;

    public static ApiResponse success(MessageCode code) {
        return ApiResponse.builder().success(true).code(code).build();
    }

    public static ApiResponse error(MessageCode code) {
        return ApiResponse.builder().success(false).code(code).build();
    }

    public static ApiResponse success(String message) {
        return ApiResponse.builder().success(true).message(message).build();
    }

    public static ApiResponse error(String message) {
        return ApiResponse.builder().success(false).message(message).build();
    }
}
