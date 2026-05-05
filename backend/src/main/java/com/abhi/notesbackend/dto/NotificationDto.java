package com.abhi.notesbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class NotificationDto {
    private UUID id;
    private UUID noteId;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}
