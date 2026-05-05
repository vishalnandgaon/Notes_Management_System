package com.abhi.notesbackend.repository;

import com.abhi.notesbackend.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {
    List<Note> findByOwnerId(UUID ownerId);
}
