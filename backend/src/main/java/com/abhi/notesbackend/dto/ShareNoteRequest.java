package com.abhi.notesbackend.dto;

import com.abhi.notesbackend.model.SharePermission;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ShareNoteRequest {
    private String username;
    private UUID teamId;

    @NotNull
    private SharePermission permission;
}
