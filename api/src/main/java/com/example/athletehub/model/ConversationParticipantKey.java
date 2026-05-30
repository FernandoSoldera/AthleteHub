package com.example.athletehub.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link ConversationParticipant}.
 * JPA's {@code @IdClass} requires a serializable POJO mirroring the
 * entity's id fields with {@code equals}/{@code hashCode}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConversationParticipantKey implements Serializable {
    private Long conversationId;
    private Long userId;
}
