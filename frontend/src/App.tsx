/* eslint-disable react-hooks/set-state-in-effect */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Bell, Clock3, LogOut, MessageSquare, Plus, Save, Search, Share2, Tag as TagIcon, Trash2, Users } from 'lucide-react';
import NoteEditor from './components/NoteEditor';
import { useAuthStore } from './store/useAuthStore';
import api from './utils/api';

interface Note {
  id: string;
  title: string;
  content: string;
  tags: string[];
  owner: boolean;
  permission: 'OWNER' | 'READ' | 'WRITE' | 'NONE';
  createdAt: string;
  updatedAt: string;
}

interface Share {
  id: string;
  username?: string;
  teamId?: string;
  teamName?: string;
  permission: 'READ' | 'WRITE';
}

interface Version {
  id: string;
  title: string;
  content: string;
  savedAt: string;
}

interface Comment {
  id: string;
  body: string;
  authorUsername: string;
  createdAt: string;
}

interface Team {
  id: string;
  name: string;
  ownerUsername: string;
  members: string[];
}

interface Notification {
  id: string;
  noteId?: string;
  message: string;
  read: boolean;
  createdAt: string;
}

const emptyNote = (): Pick<Note, 'title' | 'content' | 'tags'> => ({
  title: 'New Note',
  content: '<p>Start typing...</p>',
  tags: [],
});

function App() {
  const { user, token, logout } = useAuthStore();
  const [notes, setNotes] = useState<Note[]>([]);
  const [selectedNote, setSelectedNote] = useState<Note | null>(null);
  const [search, setSearch] = useState('');
  const [tagDraft, setTagDraft] = useState('');
  const [shareTarget, setShareTarget] = useState('');
  const [sharePermission, setSharePermission] = useState<'READ' | 'WRITE'>('WRITE');
  const [teamName, setTeamName] = useState('');
  const [teamMembers, setTeamMembers] = useState('');
  const [commentDraft, setCommentDraft] = useState('');
  const [shares, setShares] = useState<Share[]>([]);
  const [versions, setVersions] = useState<Version[]>([]);
  const [comments, setComments] = useState<Comment[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [status, setStatus] = useState('Ready');
  const [dirty, setDirty] = useState(false);

  const canWrite = selectedNote?.owner || selectedNote?.permission === 'WRITE';

  const updateNoteInState = useCallback((note: Note) => {
    setNotes((current) => {
      const exists = current.some((item) => item.id === note.id);
      const next = exists ? current.map((item) => (item.id === note.id ? note : item)) : [note, ...current];
      return next.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
    });
    setSelectedNote((current) => (current?.id === note.id ? note : current));
  }, []);

  const loadNotes = useCallback(async () => {
    const response = await api.get<Note[]>('/notes');
    setNotes(response.data);
    setSelectedNote((current) => current ?? response.data[0] ?? null);
  }, []);

  const loadWorkspaceData = useCallback(async (noteId?: string) => {
    const [teamsResponse, notificationsResponse] = await Promise.all([
      api.get<Team[]>('/teams'),
      api.get<Notification[]>('/notifications'),
    ]);
    setTeams(teamsResponse.data);
    setNotifications(notificationsResponse.data);

    if (noteId) {
      const [commentsResponse, versionsResponse] = await Promise.all([
        api.get<Comment[]>(`/notes/${noteId}/comments`),
        api.get<Version[]>(`/notes/${noteId}/versions`),
      ]);
      setComments(commentsResponse.data);
      setVersions(versionsResponse.data);
    }
  }, []);

  const loadOwnerData = useCallback(async (note: Note | null) => {
    if (!note?.owner) {
      setShares([]);
      return;
    }
    const response = await api.get<Share[]>(`/notes/${note.id}/shares`);
    setShares(response.data);
  }, []);

  useEffect(() => {
    void loadNotes().catch(() => setStatus('Could not load notes'));
    void loadWorkspaceData().catch(() => setStatus('Could not load workspace data'));
  }, [loadNotes, loadWorkspaceData]);

  useEffect(() => {
    void loadOwnerData(selectedNote).catch(() => setStatus('Could not load shares'));
    if (selectedNote) {
      setTagDraft(selectedNote.tags.join(', '));
      void loadWorkspaceData(selectedNote.id).catch(() => setStatus('Could not load note activity'));
    }
  }, [loadOwnerData, loadWorkspaceData, selectedNote]);

  useEffect(() => {
    if (!token) {
      return;
    }
    const source = new EventSourcePolyfill(token, (notification) => {
      setNotifications((current) => [notification, ...current]);
    });
    return () => source.close();
  }, [token]);

  useEffect(() => {
    if (!selectedNote || !canWrite || !dirty) {
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        const response = await api.put<Note>(`/notes/${selectedNote.id}`, selectedNote);
        updateNoteInState(response.data);
        setDirty(false);
        setStatus('Saved');
      } catch {
        setStatus('Save failed');
      }
    }, 900);
    return () => window.clearTimeout(timer);
  }, [canWrite, dirty, selectedNote, updateNoteInState]);

  const filteredNotes = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) {
      return notes;
    }
    return notes.filter((note) =>
      note.title.toLowerCase().includes(term) ||
      note.tags.some((tag) => tag.toLowerCase().includes(term)) ||
      note.content.toLowerCase().includes(term)
    );
  }, [notes, search]);

  const createNote = async () => {
    const response = await api.post<Note>('/notes', emptyNote());
    setNotes((current) => [response.data, ...current]);
    setSelectedNote(response.data);
    setDirty(false);
    setStatus('Created');
  };

  const deleteNote = async () => {
    if (!selectedNote || !selectedNote.owner) {
      return;
    }
    await api.delete(`/notes/${selectedNote.id}`);
    setNotes((current) => current.filter((note) => note.id !== selectedNote.id));
    setSelectedNote(notes.find((note) => note.id !== selectedNote.id) ?? null);
    setStatus('Deleted');
  };

  const saveTags = () => {
    if (!selectedNote || !canWrite) {
      return;
    }
    setSelectedNote({
      ...selectedNote,
      tags: tagDraft.split(',').map((tag) => tag.trim()).filter(Boolean),
    });
    setDirty(true);
  };

  const shareNote = async () => {
    if (!selectedNote?.owner || !shareTarget.trim()) {
      return;
    }
    const selectedTeam = teams.find((team) => team.name.toLowerCase() === shareTarget.trim().toLowerCase());
    const payload = selectedTeam
      ? { teamId: selectedTeam.id, permission: sharePermission }
      : { username: shareTarget.trim(), permission: sharePermission };
    const response = await api.post<Share>(`/notes/${selectedNote.id}/shares`, payload);
    setShares((current) => [response.data, ...current.filter((share) => share.id !== response.data.id)]);
    setShareTarget('');
    setStatus('Shared');
  };

  const createTeam = async () => {
    if (!teamName.trim()) {
      return;
    }
    const response = await api.post<Team>('/teams', {
      name: teamName.trim(),
      usernames: teamMembers.split(',').map((name) => name.trim()).filter(Boolean),
    });
    setTeams((current) => [response.data, ...current]);
    setTeamName('');
    setTeamMembers('');
    setStatus('Team created');
  };

  const addComment = async () => {
    if (!selectedNote || !commentDraft.trim()) {
      return;
    }
    const response = await api.post<Comment>(`/notes/${selectedNote.id}/comments`, { body: commentDraft.trim() });
    setComments((current) => [...current, response.data]);
    setCommentDraft('');
  };

  const restoreVersion = async (versionId: string) => {
    if (!selectedNote || !canWrite) {
      return;
    }
    const response = await api.post<Note>(`/notes/${selectedNote.id}/versions/${versionId}/restore`);
    updateNoteInState(response.data);
    setDirty(false);
    setStatus('Version restored');
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-top">
          <div className="brand-row">
            <div>
              <h1>Notes</h1>
              <p>{status}</p>
            </div>
            <button type="button" className="icon-button primary" onClick={createNote} title="Create note">
              <Plus size={18} />
            </button>
          </div>
          <div className="search-box">
            <Search size={17} />
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search title, tag, content" />
          </div>
        </div>

        <div className="note-list">
          {filteredNotes.map((note) => (
            <button key={note.id} type="button" className={selectedNote?.id === note.id ? 'note-item active' : 'note-item'} onClick={() => { setSelectedNote(note); setDirty(false); }}>
              <span className="note-title">{note.title}</span>
              <span className="note-meta">{note.owner ? 'Owner' : note.permission} · {new Date(note.updatedAt).toLocaleDateString()}</span>
              <span className="tag-row">
                {note.tags.slice(0, 3).map((tag) => <span key={tag}>{tag}</span>)}
              </span>
            </button>
          ))}
        </div>

        <div className="account-row">
          <div className="avatar">{user?.username?.charAt(0).toUpperCase()}</div>
          <div>
            <strong>{user?.username}</strong>
            <span>{notifications.filter((item) => !item.read).length} unread</span>
          </div>
          <button type="button" className="icon-button danger" onClick={logout} title="Logout">
            <LogOut size={17} />
          </button>
        </div>
      </aside>

      <main className="workspace">
        {selectedNote ? (
          <>
            <section className="editor-panel">
              <div className="title-row">
                <input disabled={!canWrite} value={selectedNote.title} onChange={(event) => { setSelectedNote({ ...selectedNote, title: event.target.value }); setDirty(true); }} />
                <div className="title-actions">
                  <button type="button" className="icon-button" onClick={() => setStatus('Saved')} title="Autosave is active"><Save size={17} /></button>
                  {selectedNote.owner && <button type="button" className="icon-button danger" onClick={deleteNote} title="Delete note"><Trash2 size={17} /></button>}
                </div>
              </div>
              <div className="tag-editor">
                <TagIcon size={16} />
                <input disabled={!canWrite} value={tagDraft} onChange={(event) => setTagDraft(event.target.value)} onBlur={saveTags} placeholder="tags separated by commas" />
              </div>
              <NoteEditor
                key={selectedNote.id}
                noteId={selectedNote.id}
                initialContent={selectedNote.content}
                canWrite={Boolean(canWrite)}
                onChange={(content) => { setSelectedNote({ ...selectedNote, content }); setDirty(true); }}
                onRemoteChange={(content) => { updateNoteInState({ ...selectedNote, content }); setDirty(false); }}
              />
            </section>

            <aside className="detail-panel">
              <section>
                <h2><Share2 size={16} /> Sharing</h2>
                {selectedNote.owner ? (
                  <>
                    <div className="inline-form">
                      <input value={shareTarget} onChange={(event) => setShareTarget(event.target.value)} placeholder="username or team name" />
                      <select value={sharePermission} onChange={(event) => setSharePermission(event.target.value as 'READ' | 'WRITE')}>
                        <option value="WRITE">WRITE</option>
                        <option value="READ">READ</option>
                      </select>
                      <button type="button" onClick={shareNote}>Share</button>
                    </div>
                    <div className="mini-list">
                      {shares.map((share) => <span key={share.id}>{share.username ?? share.teamName} · {share.permission}</span>)}
                    </div>
                  </>
                ) : (
                  <p className="muted">Shared with {selectedNote.permission.toLowerCase()} access.</p>
                )}
              </section>

              <section>
                <h2><Users size={16} /> Teams</h2>
                <div className="inline-form stacked">
                  <input value={teamName} onChange={(event) => setTeamName(event.target.value)} placeholder="team name" />
                  <input value={teamMembers} onChange={(event) => setTeamMembers(event.target.value)} placeholder="member usernames, comma separated" />
                  <button type="button" onClick={createTeam}>Create team</button>
                </div>
                <div className="mini-list">
                  {teams.map((team) => <span key={team.id}>{team.name} · {team.members.length} members</span>)}
                </div>
              </section>

              <section>
                <h2><MessageSquare size={16} /> Comments</h2>
                <div className="inline-form">
                  <input value={commentDraft} onChange={(event) => setCommentDraft(event.target.value)} placeholder="add a comment" />
                  <button type="button" onClick={addComment}>Post</button>
                </div>
                <div className="activity-list">
                  {comments.map((comment) => (
                    <div key={comment.id}>
                      <strong>{comment.authorUsername}</strong>
                      <p>{comment.body}</p>
                    </div>
                  ))}
                </div>
              </section>
            </aside>

            <aside className="right-panel">
              <section>
                <h2><Clock3 size={16} /> Versions</h2>
                <div className="activity-list">
                  {versions.map((version) => (
                    <button key={version.id} type="button" onClick={() => restoreVersion(version.id)} disabled={!canWrite}>
                      <strong>{version.title}</strong>
                      <span>{new Date(version.savedAt).toLocaleString()}</span>
                    </button>
                  ))}
                </div>
              </section>
              <section>
                <h2><Bell size={16} /> Notifications</h2>
                <div className="activity-list">
                  {notifications.map((notification) => (
                    <div key={notification.id} className={notification.read ? 'read' : ''}>
                      <p>{notification.message}</p>
                      <span>{new Date(notification.createdAt).toLocaleString()}</span>
                    </div>
                  ))}
                </div>
              </section>
            </aside>
          </>
        ) : (
          <div className="empty-state">
            <button type="button" className="icon-button primary" onClick={createNote}><Plus size={22} /></button>
            <p>Create your first note to begin.</p>
          </div>
        )}
      </main>
    </div>
  );
}

class EventSourcePolyfill {
  private client: Client;

  constructor(token: string, onNotification: (notification: Notification) => void) {
    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8081/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        this.client.subscribe('/user/queue/notifications', (message) => {
          onNotification(JSON.parse(message.body));
        });
      },
    });
    this.client.activate();
  }

  close() {
    void this.client.deactivate();
  }
}

export default App;
