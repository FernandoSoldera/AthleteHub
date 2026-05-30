package com.example.athletehub.dto;

import com.example.athletehub.model.Message;

import java.time.OffsetDateTime;

/**
 * One message as exposed to the client. {@code attachmentMediaId} stays
 * here as a nullable long so the model survives AH-092's media table
 * arrival without a DTO churn — the field is null at MVP since the
 * upload path doesn't exist yet.
 */
public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String body,
        Long attachmentMediaId,
        OffsetDateTime createdAt
) {
    public static MessageDto from(Message m) {
        return new MessageDto(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getBody(),
                m.getAttachmentMediaId(),
                m.getCreatedAt());
    }
}
