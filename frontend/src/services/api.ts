const API_BASE = '/api';

export interface FileInfo {
  id: string;
  originalName: string;
  size: number;
  mimeType: string;
  uploadTime: string;
  expiredAt: string;
}

export interface ChatRequest {
  message: string;
  fileIds: string[];
}

export async function uploadFiles(files: File[]): Promise<FileInfo[]> {
  const formData = new FormData();
  files.forEach(f => formData.append('files', f));

  const res = await fetch(`${API_BASE}/files/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(errText || `上传失败 (${res.status})`);
  }
  return res.json();
}

export function getDownloadUrl(fileId: string): string {
  return `${API_BASE}/files/download/${fileId}`;
}

/**
 * SSE 流式对话
 * 处理 Spring WebFlux 的 TEXT_EVENT_STREAM_VALUE 格式:
 *   data:content\n\n
 * 可能带 JSON 引号: data:"content"\n\n
 */
export function chatStream(
  request: ChatRequest,
  onText: (text: string) => void,
  onError: (error: string) => void,
  onDone: () => void
): AbortController {
  const controller = new AbortController();

  fetch(`${API_BASE}/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal: controller.signal,
  })
    .then(async response => {
      if (!response.ok) {
        const errText = await response.text().catch(() => '');
        onError(errText || `服务器错误: ${response.status}`);
        onDone();
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        onError('无法读取响应流');
        onDone();
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';
      let streamDone = false;

      while (!streamDone) {
        const { done, value } = await reader.read();
        streamDone = done;

        buffer += decoder.decode(value || new Uint8Array(), { stream: !done });

        // SSE 消息以 \n\n 分隔
        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';

        for (const part of parts) {
          // 每个 SSE event 可能包含多行 data:（Spring WebFlux 用多行 data: 编码换行）
          const lines = part.split('\n');
          const dataLines: string[] = [];
          for (const l of lines) {
            const trimmed = l.trim();
            if (trimmed.startsWith('data:')) {
              dataLines.push(trimmed.slice(5));
            }
            // event:/id: 等元数据行直接跳过
          }
          let data = dataLines.join('\n').trim();

          if (!data || data === '[DONE]') continue;

          // 移除 JSON 字符串的引号: "content" -> content
          if (data.startsWith('"') && data.endsWith('"')) {
            try {
              data = JSON.parse(data);
            } catch {
              data = data.slice(1, -1);
            }
          }

          // 处理转义
          data = data.replace(/\\n/g, '\n');

          if (data) onText(data);
        }
      }

      onDone();
    })
    .catch(err => {
      if (err.name !== 'AbortError') {
        onError(err.message);
      }
      onDone();
    });

  return controller;
}
