package com.abhi.notesbackend.controller;

import com.abhi.notesbackend.dto.*;
import com.abhi.notesbackend.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NoteController {
    private final NoteService noteService;

    @GetMapping("/notes")
    public ResponseEntity<List<NoteDto>> getAllNotes(Authentication authentication) {
        return ResponseEntity.ok(noteService.getUserNotes(authentication.getName()));
    }

    @GetMapping("/notes/{noteId}")
    public ResponseEntity<NoteDto> getNote(@PathVariable UUID noteId, Authentication authentication) {
        return ResponseEntity.ok(noteService.getNote(authentication.getName(), noteId));
    }

    @PostMapping("/notes")
    public ResponseEntity<NoteDto> createNote(@RequestBody NoteDto noteDto, Authentication authentication) {
        return ResponseEntity.ok(noteService.createNote(authentication.getName(), noteDto));
    }

    @PutMapping("/notes/{noteId}")
    public ResponseEntity<NoteDto> updateNote(@PathVariable UUID noteId, @RequestBody NoteDto noteDto, Authentication authentication) {
        return ResponseEntity.ok(noteService.updateNote(authentication.getName(), noteId, noteDto));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID noteId, Authentication authentication) {
        noteService.deleteNote(authentication.getName(), noteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notes/{noteId}/shares")
    public ResponseEntity<List<ShareDto>> getShares(@PathVariable UUID noteId, Authentication authentication) {
        return ResponseEntity.ok(noteService.getShares(authentication.getName(), noteId));
    }

    @PostMapping("/notes/{noteId}/shares")
    public ResponseEntity<ShareDto> shareNote(@PathVariable UUID noteId, @Valid @RequestBody ShareNoteRequest request, Authentication authentication) {
        return ResponseEntity.ok(noteService.shareNote(authentication.getName(), noteId, request));
    }

    @DeleteMapping("/notes/{noteId}/shares/{shareId}")
    public ResponseEntity<Void> removeShare(@PathVariable UUID noteId, @PathVariable UUID shareId, Authentication authentication) {
        noteService.removeShare(authentication.getName(), noteId, shareId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notes/{noteId}/versions")
    public ResponseEntity<List<NoteVersionDto>> getVersions(@PathVariable UUID noteId, Authentication authentication) {
        return ResponseEntity.ok(noteService.getVersions(authentication.getName(), noteId));
    }

    @PostMapping("/notes/{noteId}/versions/{versionId}/restore")
    public ResponseEntity<NoteDto> restoreVersion(@PathVariable UUID noteId, @PathVariable UUID versionId, Authentication authentication) {
        return ResponseEntity.ok(noteService.restoreVersion(authentication.getName(), noteId, versionId));
    }

    @GetMapping("/notes/{noteId}/comments")
    public ResponseEntity<List<CommentDto>> getComments(@PathVariable UUID noteId, Authentication authentication) {
        return ResponseEntity.ok(noteService.getComments(authentication.getName(), noteId));
    }

    @PostMapping("/notes/{noteId}/comments")
    public ResponseEntity<CommentDto> addComment(@PathVariable UUID noteId, @Valid @RequestBody CommentRequest request, Authentication authentication) {
        return ResponseEntity.ok(noteService.addComment(authentication.getName(), noteId, request));
    }

    @GetMapping("/teams")
    public ResponseEntity<List<TeamDto>> getTeams(Authentication authentication) {
        return ResponseEntity.ok(noteService.getTeams(authentication.getName()));
    }

    @PostMapping("/teams")
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody TeamRequest request, Authentication authentication) {
        return ResponseEntity.ok(noteService.createTeam(authentication.getName(), request));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationDto>> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(noteService.getNotifications(authentication.getName()));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable UUID notificationId, Authentication authentication) {
        noteService.markNotificationRead(authentication.getName(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
