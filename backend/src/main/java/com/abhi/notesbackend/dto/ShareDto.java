package com.abhi.notesbackend.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ShareDto {
    private UUID id;
    private String username;
    private UUID teamId;
    private String teamName;
    private String permission;
}
