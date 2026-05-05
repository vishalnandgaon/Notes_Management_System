package com.abhi.notesbackend.repository;

import com.abhi.notesbackend.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findByUserId(UUID userId);
    List<TeamMember> findByTeamId(UUID teamId);
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);
    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);
}
