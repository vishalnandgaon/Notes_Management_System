package com.abhi.notesbackend.dto;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class TeamDto {
    private UUID id;
    private String name;
    private String ownerUsername;
    private Set<String> members;
}
