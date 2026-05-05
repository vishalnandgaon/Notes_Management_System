package com.abhi.notesbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class NoteVersionDto {
    private UUID id;
    private String title;
    private String content;
    private LocalDateTime savedAt;
}
