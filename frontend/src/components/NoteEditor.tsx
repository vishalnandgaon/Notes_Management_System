import { useEffect, useRef } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Bold, Italic, List, ListOrdered } from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';

interface NoteEditorProps {
  noteId: string;
  initialContent: string;
  canWrite: boolean;
  onChange: (content: string) => void;
  onRemoteChange: (content: string) => void;
}

const NoteEditor = ({ noteId, initialContent, canWrite, onChange, onRemoteChange }: NoteEditorProps) => {
  const token = useAuthStore((state) => state.token);
  const clientRef = useRef<Client | null>(null);

  const editor = useEditor({
    extensions: [StarterKit],
    content: initialContent,
    editable: canWrite,
    editorProps: {
      attributes: {
        class: 'editor-surface',
      },
    },
    onUpdate: ({ editor }) => {
      const html = editor.getHTML();
      onChange(html);
      if (clientRef.current?.connected) {
        clientRef.current.publish({
          destination: `/app/note/${noteId}/edit`,
          body: JSON.stringify({ id: noteId, content: html }),
        });
      }
    },
  });

  useEffect(() => {
    editor?.setEditable(canWrite);
  }, [canWrite, editor]);

  useEffect(() => {
    if (!editor || !token) {
      return;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8081/ws'),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/note/${noteId}`, (message) => {
          const data = JSON.parse(message.body);
          if (data.id === noteId && typeof data.content === 'string' && data.content !== editor.getHTML()) {
            editor.commands.setContent(data.content, { emitUpdate: false });
            onRemoteChange(data.content);
          }
        });
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
  }, [editor, noteId, onRemoteChange, token]);

  useEffect(() => {
    if (editor && initialContent !== editor.getHTML()) {
      editor.commands.setContent(initialContent || '', { emitUpdate: false });
    }
  }, [editor, initialContent]);

  if (!editor) {
    return null;
  }

  return (
    <div className="editor-wrap">
      <div className="editor-toolbar">
        <button type="button" disabled={!canWrite} onClick={() => editor.chain().focus().toggleBold().run()} className={editor.isActive('bold') ? 'tool active' : 'tool'} title="Bold">
          <Bold size={16} />
        </button>
        <button type="button" disabled={!canWrite} onClick={() => editor.chain().focus().toggleItalic().run()} className={editor.isActive('italic') ? 'tool active' : 'tool'} title="Italic">
          <Italic size={16} />
        </button>
        <button type="button" disabled={!canWrite} onClick={() => editor.chain().focus().toggleBulletList().run()} className={editor.isActive('bulletList') ? 'tool active' : 'tool'} title="Bulleted list">
          <List size={16} />
        </button>
        <button type="button" disabled={!canWrite} onClick={() => editor.chain().focus().toggleOrderedList().run()} className={editor.isActive('orderedList') ? 'tool active' : 'tool'} title="Numbered list">
          <ListOrdered size={16} />
        </button>
      </div>
      <EditorContent editor={editor} />
    </div>
  );
};

export default NoteEditor;
