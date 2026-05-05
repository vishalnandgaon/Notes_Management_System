package com.abhi.notesbackend.repository;

import com.abhi.notesbackend.model.NoteVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteVersionRepository extends JpaRepository<NoteVersion, UUID> {
    List<NoteVersion> findByNoteIdOrderBySavedAtDesc(UUID noteId);
}
