package com.abhi.notesbackend.controller;

import com.abhi.notesbackend.dto.NoteDto;
import com.abhi.notesbackend.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WebSocketController {
    private final NoteService noteService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/note/{noteId}/edit")
    public void handleNoteEdit(@DestinationVariable UUID noteId, @Payload NoteDto noteDto, Principal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication is required for live editing.");
        }
        NoteDto saved = noteService.updateRealtimeContent(principal.getName(), noteId, noteDto);
        messagingTemplate.convertAndSend("/topic/note/" + noteId, saved);
    }
}
