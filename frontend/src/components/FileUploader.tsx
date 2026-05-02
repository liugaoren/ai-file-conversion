import React, { useRef, useState, DragEvent } from 'react';
import { FileInfo, uploadFiles } from '../services/api';

interface Props {
  onFilesUploaded: (files: FileInfo[]) => void;
  disabled?: boolean;
}

export default function FileUploader({ onFilesUploaded, disabled }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);

  const handleFiles = async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;

    setUploading(true);
    try {
      const files = Array.from(fileList);
      const result = await uploadFiles(files);
      onFilesUploaded(result);
    } catch (err: any) {
      alert('上传失败：' + err.message);
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    handleFiles(e.dataTransfer.files);
  };

  return (
    <div
      className={`upload-area ${dragOver ? 'drag-over' : ''}`}
      onDragOver={e => { e.preventDefault(); setDragOver(true); }}
      onDragLeave={() => setDragOver(false)}
      onDrop={handleDrop}
    >
      <input
        ref={inputRef}
        type="file"
        multiple
        disabled={disabled || uploading}
        onChange={e => handleFiles(e.target.files)}
      />
      <div className="upload-hint">
        <span className="upload-icon">{uploading ? '⏳' : '📂'}</span>
        <div className="upload-text">
          {uploading ? '上传中...' : '点击或拖拽文件到此处上传'}
        </div>
        <div className="upload-sub">支持多文件批量上传</div>
      </div>
    </div>
  );
}
