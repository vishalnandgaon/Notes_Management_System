package com.abhi.notesbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CommentDto {
    private UUID id;
    private String body;
    private String authorUsername;
    private LocalDateTime createdAt;
}
