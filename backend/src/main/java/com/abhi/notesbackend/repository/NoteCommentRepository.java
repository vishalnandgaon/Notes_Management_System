package com.abhi.notesbackend.repository;

import com.abhi.notesbackend.model.NoteComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteCommentRepository extends JpaRepository<NoteComment, UUID> {
    List<NoteComment> findByNoteIdOrderByCreatedAtAsc(UUID noteId);
}
