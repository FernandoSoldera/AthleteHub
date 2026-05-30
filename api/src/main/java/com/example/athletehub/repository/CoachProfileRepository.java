package com.example.athletehub.repository;

import com.example.athletehub.model.CoachProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachProfileRepository extends JpaRepository<CoachProfile, Long> {
}
