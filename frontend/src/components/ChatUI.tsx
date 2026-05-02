import React, { useEffect, useRef } from 'react';

export interface Message {
  role: 'user' | 'assistant' | 'error';
  content: string;
  fileUrl?: string;
  fileName?: string;
  isHtml?: boolean;
}

interface Props {
  messages: Message[];
  loading: boolean;
}

export default function ChatUI({ messages, loading }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  return (
    <div className="chat-window">
      {messages.length === 0 && !loading && (
        <div className="empty-hint">
          <div className="empty-icon">📄</div>
          <div>上传文件，然后告诉我想转换成什么格式</div>
          <div className="empty-examples">
            例如："把这个 Word 转成 PDF" 或 "批量转成 JPG"
          </div>
        </div>
      )}

      {messages.map((msg, i) => (
        <div key={i} className={`message ${msg.role}`}>
          {msg.role === 'user' ? (
            <div>{msg.content}</div>
          ) : (
            <>
              <div className="assistant-text">
                {msg.isHtml
                  ? <div dangerouslySetInnerHTML={{ __html: msg.content }} />
                  : msg.content}
              </div>

              {msg.fileUrl && (
                <div className="download-card">
                  <div className="download-card-icon">📥</div>
                  <div className="download-card-info">
                    <div className="download-card-title">{msg.fileName || '转换后的文件'}</div>
                    <div className="download-card-name">点击下载转换好的文件</div>
                  </div>
                  <a
                    className="download-card-btn"
                    href={msg.fileUrl}
                    download
                  >
                    下载
                  </a>
                </div>
              )}
            </>
          )}

          {msg.role === 'error' && (
            <div>{msg.content}</div>
          )}
        </div>
      ))}

      {loading && <div className="message loading">⏳ 处理中...</div>}
      <div ref={bottomRef} />
    </div>
  );
}
