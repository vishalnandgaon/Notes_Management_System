package com.abhi.notesbackend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class NoteDto {
    private UUID id;
    private String title;
    private String content;
    private Set<String> tags;
    private boolean owner;
    private String permission;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
