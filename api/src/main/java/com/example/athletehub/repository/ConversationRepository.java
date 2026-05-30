package com.example.athletehub.repository;

import com.example.athletehub.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Look up the (at-MVP, single) conversation tagged to this coach_athlete row. */
    Optional<Conversation> findByCoachAthleteId(Long coachAthleteId);
}
