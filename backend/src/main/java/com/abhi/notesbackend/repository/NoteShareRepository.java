package com.abhi.notesbackend.repository;

import com.abhi.notesbackend.model.NoteShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteShareRepository extends JpaRepository<NoteShare, UUID> {
    List<NoteShare> findByUserId(UUID userId);
    List<NoteShare> findByTeamIdIn(List<UUID> teamIds);
    List<NoteShare> findByNoteId(UUID noteId);
    Optional<NoteShare> findByNoteIdAndUserId(UUID noteId, UUID userId);
    Optional<NoteShare> findByNoteIdAndTeamId(UUID noteId, UUID teamId);

    @Query("""
            select s from NoteShare s
            where s.note.id = :noteId
            and s.team.id in :teamIds
            """)
    List<NoteShare> findTeamSharesForNote(@Param("noteId") UUID noteId, @Param("teamIds") List<UUID> teamIds);
}
