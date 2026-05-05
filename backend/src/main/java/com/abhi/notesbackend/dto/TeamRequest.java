package com.abhi.notesbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class TeamRequest {
    @NotBlank
    private String name;

    private Set<String> usernames;
}
