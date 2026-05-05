package com.abhi.notesbackend.service;

import com.abhi.notesbackend.dto.*;
import com.abhi.notesbackend.model.*;
import com.abhi.notesbackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {
    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NoteShareRepository noteShareRepository;
    private final NoteVersionRepository noteVersionRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NoteCommentRepository noteCommentRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<NoteDto> getUserNotes(String username) {
        User user = getUser(username);
        Map<UUID, Note> notes = new LinkedHashMap<>();
        noteRepository.findByOwnerId(user.getId()).forEach(note -> notes.put(note.getId(), note));
        noteShareRepository.findByUserId(user.getId()).forEach(share -> notes.put(share.getNote().getId(), share.getNote()));

        List<UUID> teamIds = teamMemberRepository.findByUserId(user.getId())
                .stream()
                .map(member -> member.getTeam().getId())
                .toList();
        if (!teamIds.isEmpty()) {
            noteShareRepository.findByTeamIdIn(teamIds)
                    .forEach(share -> notes.put(share.getNote().getId(), share.getNote()));
        }

        return notes.values().stream()
                .map(note -> mapToDto(note, user))
                .sorted(Comparator.comparing(NoteDto::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteDto getNote(String username, UUID noteId) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireCanView(user, note);
        return mapToDto(note, user);
    }

    @Transactional
    public NoteDto createNote(String username, NoteDto noteDto) {
        User user = getUser(username);
        Note note = new Note();
        note.setTitle(normalizeTitle(noteDto.getTitle()));
        note.setContent(defaultContent(noteDto.getContent()));
        note.setOwner(user);
        note.setTags(resolveTags(noteDto.getTags()));
        return mapToDto(noteRepository.save(note), user);
    }

    @Transactional
    public NoteDto updateNote(String username, UUID noteId, NoteDto noteDto) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireCanWrite(user, note);
        snapshot(note);

        note.setTitle(normalizeTitle(noteDto.getTitle()));
        note.setContent(defaultContent(noteDto.getContent()));
        note.setTags(resolveTags(noteDto.getTags()));

        Note saved = noteRepository.save(note);
        notifyCollaborators(saved, user, user.getUsername() + " updated \"" + saved.getTitle() + "\"");
        messagingTemplate.convertAndSend("/topic/note/" + saved.getId(), mapToDto(saved, user));
        return mapToDto(saved, user);
    }

    @Transactional
    public NoteDto updateRealtimeContent(String username, UUID noteId, NoteDto noteDto) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireCanWrite(user, note);
        snapshot(note);
        if (StringUtils.hasText(noteDto.getTitle())) {
            note.setTitle(noteDto.getTitle().trim());
        }
        note.setContent(defaultContent(noteDto.getContent()));
        Note saved = noteRepository.save(note);
        notifyCollaborators(saved, user, user.getUsername() + " edited \"" + saved.getTitle() + "\"");
        return mapToDto(saved, user);
    }

    @Transactional
    public void deleteNote(String username, UUID noteId) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        if (!note.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Only the owner can delete this note.");
        }
        noteRepository.delete(note);
    }

    @Transactional
    public ShareDto shareNote(String username, UUID noteId, ShareNoteRequest request) {
        User owner = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireOwner(owner, note);

        if (StringUtils.hasText(request.getUsername())) {
            User recipient = userRepository.findByUsername(request.getUsername().trim())
                    .orElseThrow(() -> new NoSuchElementException("User not found."));
            NoteShare share = noteShareRepository.findByNoteIdAndUserId(noteId, recipient.getId()).orElseGet(NoteShare::new);
            share.setNote(note);
            share.setUser(recipient);
            share.setTeam(null);
            share.setPermission(request.getPermission());
            NoteShare saved = noteShareRepository.save(share);
            createNotification(recipient, note, owner.getUsername() + " shared \"" + note.getTitle() + "\" with you");
            return mapShare(saved);
        }

        if (request.getTeamId() != null) {
            Team team = teamRepository.findById(request.getTeamId())
                    .orElseThrow(() -> new NoSuchElementException("Team not found."));
            requireTeamOwner(owner, team);
            NoteShare share = noteShareRepository.findByNoteIdAndTeamId(noteId, team.getId()).orElseGet(NoteShare::new);
            share.setNote(note);
            share.setUser(null);
            share.setTeam(team);
            share.setPermission(request.getPermission());
            NoteShare saved = noteShareRepository.save(share);
            teamMemberRepository.findByTeamId(team.getId())
                    .forEach(member -> createNotification(member.getUser(), note, owner.getUsername() + " shared \"" + note.getTitle() + "\" with " + team.getName()));
            return mapShare(saved);
        }

        throw new IllegalArgumentException("Provide either username or teamId.");
    }

    @Transactional(readOnly = true)
    public List<ShareDto> getShares(String username, UUID noteId) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireOwner(user, note);
        return noteShareRepository.findByNoteId(noteId).stream().map(this::mapShare).toList();
    }

    @Transactional
    public void removeShare(String username, UUID noteId, UUID shareId) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireOwner(user, note);
        NoteShare share = noteShareRepository.findById(shareId).orElseThrow();
        if (!share.getNote().getId().equals(note.getId())) {
            throw new IllegalArgumentException("Share does not belong to this note.");
        }
        noteShareRepository.delete(share);
    }

    @Transactional(readOnly = true)
    public List<NoteVersionDto> getVersions(String username, UUID noteId) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireCanView(user, note);
        return noteVersionRepository.findByNoteIdOrderBySavedAtDesc(noteId).stream().map(this::mapVersion).toList();
    }

    @Transactional
    public NoteDto restoreVersion(String username, UUID noteId, UUID versionId) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireCanWrite(user, note);
        NoteVersion version = noteVersionRepository.findById(versionId).orElseThrow();
        if (!version.getNote().getId().equals(note.getId())) {
            throw new IllegalArgumentException("Version does not belong to this note.");
        }
        snapshot(note);
        note.setTitle(version.getTitle());
        note.setContent(version.getContent());
        Note saved = noteRepository.save(note);
        notifyCollaborators(saved, user, user.getUsername() + " restored a version of \"" + saved.getTitle() + "\"");
        messagingTemplate.convertAndSend("/topic/note/" + saved.getId(), mapToDto(saved, user));
        return mapToDto(saved, user);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> getComments(String username, UUID noteId) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireCanView(user, note);
        return noteCommentRepository.findByNoteIdOrderByCreatedAtAsc(noteId).stream().map(this::mapComment).toList();
    }

    @Transactional
    public CommentDto addComment(String username, UUID noteId, CommentRequest request) {
        User user = getUser(username);
        Note note = getNoteOrThrow(noteId);
        requireCanView(user, note);
        NoteComment comment = new NoteComment();
        comment.setNote(note);
        comment.setAuthor(user);
        comment.setBody(request.getBody().trim());
        NoteComment saved = noteCommentRepository.save(comment);
        notifyCollaborators(note, user, user.getUsername() + " commented on \"" + note.getTitle() + "\"");
        CommentDto dto = mapComment(saved);
        messagingTemplate.convertAndSend("/topic/note/" + noteId + "/comments", dto);
        return dto;
    }

    @Transactional
    public TeamDto createTeam(String username, TeamRequest request) {
        User owner = getUser(username);
        Team team = new Team();
        team.setName(request.getName().trim());
        team.setOwner(owner);
        Team saved = teamRepository.save(team);
        addTeamMember(saved, owner);
        if (request.getUsernames() != null) {
            request.getUsernames().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(memberName -> userRepository.findByUsername(memberName)
                            .orElseThrow(() -> new NoSuchElementException("User not found: " + memberName)))
                    .forEach(member -> addTeamMember(saved, member));
        }
        return mapTeam(saved);
    }

    @Transactional(readOnly = true)
    public List<TeamDto> getTeams(String username) {
        User user = getUser(username);
        Set<Team> teams = new LinkedHashSet<>(teamRepository.findByOwnerId(user.getId()));
        teamMemberRepository.findByUserId(user.getId()).forEach(member -> teams.add(member.getTeam()));
        return teams.stream().map(this::mapTeam).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(String username) {
        User user = getUser(username);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::mapNotification).toList();
    }

    @Transactional
    public void markNotificationRead(String username, UUID notificationId) {
        User user = getUser(username);
        Notification notification = notificationRepository.findById(notificationId).orElseThrow();
        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new SecurityException("Cannot update another user's notification.");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private Set<Tag> resolveTags(Set<String> tagNames) {
        if (tagNames == null) {
            return new HashSet<>();
        }
        return tagNames.stream()
                .filter(StringUtils::hasText)
                .map(name -> name.trim().toLowerCase())
                .distinct()
                .map(tagName -> tagRepository.findByName(tagName).orElseGet(() -> {
                    Tag newTag = new Tag();
                    newTag.setName(tagName);
                    return tagRepository.save(newTag);
                }))
                .collect(Collectors.toSet());
    }

    private void snapshot(Note note) {
        NoteVersion version = new NoteVersion();
        version.setNote(note);
        version.setTitle(note.getTitle());
        version.setContent(note.getContent() == null ? "" : note.getContent());
        noteVersionRepository.save(version);
    }

    private void notifyCollaborators(Note note, User actor, String message) {
        getCollaborators(note).stream()
                .filter(user -> !user.getId().equals(actor.getId()))
                .forEach(user -> createNotification(user, note, message));
    }

    private Set<User> getCollaborators(Note note) {
        Set<User> users = new LinkedHashSet<>();
        users.add(note.getOwner());
        for (NoteShare share : noteShareRepository.findByNoteId(note.getId())) {
            if (share.getUser() != null) {
                users.add(share.getUser());
            }
            if (share.getTeam() != null) {
                teamMemberRepository.findByTeamId(share.getTeam().getId()).forEach(member -> users.add(member.getUser()));
            }
        }
        return users;
    }

    private void createNotification(User recipient, Note note, String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setNote(note);
        notification.setMessage(message);
        Notification saved = notificationRepository.save(notification);
        messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/notifications", mapNotification(saved));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new NoSuchElementException("User not found."));
    }

    private Note getNoteOrThrow(UUID noteId) {
        return noteRepository.findById(noteId).orElseThrow(() -> new NoSuchElementException("Note not found."));
    }

    private void requireOwner(User user, Note note) {
        if (!note.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Only the owner can perform this action.");
        }
    }

    private void requireTeamOwner(User user, Team team) {
        if (!team.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Only the team owner can perform this action.");
        }
    }

    private void requireCanView(User user, Note note) {
        if (!canView(user, note)) {
            throw new SecurityException("You do not have access to this note.");
        }
    }

    private void requireCanWrite(User user, Note note) {
        if (!canWrite(user, note)) {
            throw new SecurityException("You do not have write access to this note.");
        }
    }

    private boolean canView(User user, Note note) {
        return note.getOwner().getId().equals(user.getId()) || getSharePermission(user, note).isPresent();
    }

    private boolean canWrite(User user, Note note) {
        if (note.getOwner().getId().equals(user.getId())) {
            return true;
        }
        return getSharePermission(user, note)
                .map(permission -> permission == SharePermission.WRITE)
                .orElse(false);
    }

    private Optional<SharePermission> getSharePermission(User user, Note note) {
        Optional<NoteShare> directShare = noteShareRepository.findByNoteIdAndUserId(note.getId(), user.getId());
        if (directShare.isPresent()) {
            return Optional.of(directShare.get().getPermission());
        }
        List<UUID> teamIds = teamMemberRepository.findByUserId(user.getId())
                .stream()
                .map(member -> member.getTeam().getId())
                .toList();
        if (teamIds.isEmpty()) {
            return Optional.empty();
        }
        return noteShareRepository.findTeamSharesForNote(note.getId(), teamIds)
                .stream()
                .map(NoteShare::getPermission)
                .max(Comparator.comparingInt(permission -> permission == SharePermission.WRITE ? 2 : 1));
    }

    private String normalizeTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : "Untitled Note";
    }

    private String defaultContent(String content) {
        return content == null ? "" : content;
    }

    private void addTeamMember(Team team, User user) {
        if (teamMemberRepository.existsByTeamIdAndUserId(team.getId(), user.getId())) {
            return;
        }
        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUser(user);
        teamMemberRepository.save(member);
    }

    private NoteDto mapToDto(Note note, User currentUser) {
        NoteDto dto = new NoteDto();
        dto.setId(note.getId());
        dto.setTitle(note.getTitle());
        dto.setContent(note.getContent());
        dto.setTags(note.getTags().stream().map(Tag::getName).collect(Collectors.toCollection(LinkedHashSet::new)));
        dto.setOwner(note.getOwner().getId().equals(currentUser.getId()));
        dto.setPermission(dto.isOwner() ? "OWNER" : getSharePermission(currentUser, note).map(Enum::name).orElse("NONE"));
        dto.setCreatedAt(note.getCreatedAt());
        dto.setUpdatedAt(note.getUpdatedAt());
        return dto;
    }

    private ShareDto mapShare(NoteShare share) {
        ShareDto dto = new ShareDto();
        dto.setId(share.getId());
        if (share.getUser() != null) {
            dto.setUsername(share.getUser().getUsername());
        }
        if (share.getTeam() != null) {
            dto.setTeamId(share.getTeam().getId());
            dto.setTeamName(share.getTeam().getName());
        }
        dto.setPermission(share.getPermission().name());
        return dto;
    }

    private NoteVersionDto mapVersion(NoteVersion version) {
        NoteVersionDto dto = new NoteVersionDto();
        dto.setId(version.getId());
        dto.setTitle(version.getTitle());
        dto.setContent(version.getContent());
        dto.setSavedAt(version.getSavedAt());
        return dto;
    }

    private CommentDto mapComment(NoteComment comment) {
        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setBody(comment.getBody());
        dto.setAuthorUsername(comment.getAuthor().getUsername());
        dto.setCreatedAt(comment.getCreatedAt());
        return dto;
    }

    private TeamDto mapTeam(Team team) {
        TeamDto dto = new TeamDto();
        dto.setId(team.getId());
        dto.setName(team.getName());
        dto.setOwnerUsername(team.getOwner().getUsername());
        dto.setMembers(teamMemberRepository.findByTeamId(team.getId()).stream()
                .map(member -> member.getUser().getUsername())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return dto;
    }

    private NotificationDto mapNotification(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setNoteId(notification.getNote() == null ? null : notification.getNote().getId());
        dto.setMessage(notification.getMessage());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}
