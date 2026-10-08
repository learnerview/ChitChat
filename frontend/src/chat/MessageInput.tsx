import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import type { ChatMessage } from '../api/types';
import { CloseIcon, SendIcon } from '../components/icons';

interface MessageInputProps {
  editing: ChatMessage | null;
  replyingTo: ChatMessage | null;
  replyAuthor: string;
  onSend: (content: string) => void;
  onCancelEdit: () => void;
  onCancelReply: () => void;
}

export default function MessageInput({
  editing,
  replyingTo,
  replyAuthor,
  onSend,
  onCancelEdit,
  onCancelReply,
}: MessageInputProps) {
  const [content, setContent] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const sendRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (editing) setContent(editing.content ?? '');
    textareaRef.current?.focus();
  }, [editing, replyingTo]);

  function submit() {
    const trimmed = content.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setContent('');
    // One-shot burst ripple on the send button.
    const btn = sendRef.current;
    if (btn) {
      btn.classList.remove('burst');
      // Force reflow so the animation can restart on rapid sends.
      void btn.offsetWidth;
      btn.classList.add('burst');
      window.setTimeout(() => btn.classList.remove('burst'), 520);
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
    if (event.key === 'Escape') {
      if (editing) onCancelEdit();
      else if (replyingTo) onCancelReply();
    }
  }

  return (
    <div className="composer">
      {editing && (
        <div className="composer-note">
          <span>Editing message</span>
          <button className="icon-btn" onClick={onCancelEdit} aria-label="Cancel edit">
            <CloseIcon />
          </button>
        </div>
      )}
      {replyingTo && (
        <div className="composer-note reply">
          <span>
            Replying to <strong>{replyAuthor}</strong>: {(replyingTo.content ?? '').slice(0, 60)}
          </span>
          <button className="icon-btn" onClick={onCancelReply} aria-label="Cancel reply">
            <CloseIcon />
          </button>
        </div>
      )}
      <div className="composer-row">
        <textarea
          ref={textareaRef}
          value={content}
          onChange={event => setContent(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder={editing ? 'Edit your message…' : 'Type a message…'}
          rows={1}
        />
        <button
          ref={sendRef}
          className="send-btn"
          onClick={submit}
          disabled={!content.trim()}
          aria-label={editing ? 'Save edit' : 'Send message'}
        >
          <SendIcon />
        </button>
      </div>
    </div>
  );
}
