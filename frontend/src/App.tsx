import React, { useState, useCallback, useRef, useEffect } from 'react';
import { FileInfo, chatStream } from './services/api';
import FileUploader from './components/FileUploader';
import ChatUI, { Message } from './components/ChatUI';

export default function App() {
  const [uploadedFiles, setUploadedFiles] = useState<FileInfo[]>([]);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [input, setInput] = useState('');
  const abortRef = useRef<AbortController | null>(null);

  // Detect download URLs in completed assistant messages
  useEffect(() => {
    if (!loading && messages.length > 0) {
      const last = messages[messages.length - 1];
      if (last.role === 'assistant' && !last.fileUrl && !last.isHtml) {
        const regex = /\/api\/files\/download\/([a-f0-9-]+)/i;
        const match = last.content.match(regex);
        if (match) {
          setMessages(prev => {
            const updated = [...prev];
            updated[updated.length - 1] = {
              ...updated[updated.length - 1],
              fileUrl: `/api/files/download/${match[1]}`,
              fileName: '转换后的文件',
            };
            return updated;
          });
        }
      }
    }
  }, [loading]);

  const handleFilesUploaded = useCallback((files: FileInfo[]) => {
    setUploadedFiles(prev => [...prev, ...files]);
  }, []);

  const removeFile = useCallback((fileId: string) => {
    setUploadedFiles(prev => prev.filter(f => f.id !== fileId));
  }, []);

  const handleSend = useCallback(async () => {
    const text = input.trim();
    if (!text || loading) return;

    setInput('');
    setLoading(true);

    const userMsg: Message = { role: 'user', content: text };
    const assistantMsg: Message = { role: 'assistant', content: '' };

    setMessages(prev => [...prev, userMsg, assistantMsg]);

    abortRef.current = chatStream(
      { message: text, fileIds: uploadedFiles.map(f => f.id) },
      (chunk) => {
        setMessages(prev => {
          const updated = [...prev];
          const last = updated[updated.length - 1];
          if (last.role === 'assistant') {
            updated[updated.length - 1] = {
              ...last,
              content: last.content + chunk,
            };
          }
          return updated;
        });
      },
      (error) => {
        setMessages(prev => [...prev, { role: 'error', content: `出错了：${error}` }]);
        setLoading(false);
      },
      () => {
        setLoading(false);
      }
    );
  }, [input, loading, uploadedFiles]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>AI 文件转换</h1>
        <p className="app-desc">上传文件后，用自然语言告诉我要转换成什么格式</p>
      </header>

      <FileUploader onFilesUploaded={handleFilesUploaded} disabled={loading} />

      {uploadedFiles.length > 0 && (
        <div className="file-list">
          {uploadedFiles.map(f => (
            <div key={f.id} className="file-tag">
              <span className="file-icon">{getFileIcon(f.originalName)}</span>
              {f.originalName}
              <span className="file-size">({formatSize(f.size)})</span>
              <span className="remove" onClick={() => removeFile(f.id)}>×</span>
            </div>
          ))}
        </div>
      )}

      <ChatUI messages={messages} loading={loading} />

      <div className="input-area">
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={uploadedFiles.length > 0 ? '输入转换指令，例如：转成 PDF' : '请先上传文件'}
          disabled={loading}
        />
        <button onClick={handleSend} disabled={loading || !input.trim()}>
          发送
        </button>
      </div>

      <div className="status-bar">
        {uploadedFiles.length > 0
          ? `已上传 ${uploadedFiles.length} 个文件`
          : '尚未上传文件'}
      </div>
    </div>
  );
}

function getFileIcon(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || '';
  switch (ext) {
    case 'doc': case 'docx': return '📝';
    case 'xls': case 'xlsx': return '📊';
    case 'pdf': return '📕';
    case 'png': case 'jpg': case 'jpeg': case 'gif': case 'bmp': return '🖼️';
    case 'md': return '📋';
    case 'csv': return '📑';
    case 'json': return '📄';
    case 'html': case 'htm': return '🌐';
    default: return '📁';
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}