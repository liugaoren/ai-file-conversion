package com.file.conversion.service;

import com.file.conversion.tools.ConversionTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final FileStorageService fileStorageService;
    private final ConversionTools conversionTools;

    public AiChatService(ChatModel chatModel,
                         ConversionTools conversionTools,
                         FileStorageService fileStorageService) {
        this.chatClient = ChatClient.builder(chatModel)
                .build();
        this.fileStorageService = fileStorageService;
        this.conversionTools = conversionTools;
    }

    public Flux<String> chat(String userMessage, List<String> fileIds) {
        String systemPrompt = buildSystemPrompt(fileIds);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(conversionTools)
                .stream()
                .content();
    }

    private String buildSystemPrompt(List<String> fileIds) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你是一个文件格式转换助手。你的职责是帮助用户将上传的文件转换成他们想要的格式。

                支持的转换类型：
                - Word (.doc/.docx) → PDF
                - Word (.doc/.docx) → TXT
                - Markdown (.md) → PDF
                - Markdown (.md) → HTML
                - PNG (.png) → JPG/JPEG
                - Excel (.xls/.xlsx) → JSON
                - Excel (.xls/.xlsx) → CSV
                - CSV → JSON
                - JSON → CSV
                - HTML → PDF

                使用规则：
                1. 用户上传文件后不要自动回复，等用户主动说出转换指令
                2. 用户说"转成XX"时，调用 convertSingleFile 工具，传入文件 ID 和目标格式
                3. 用户说"批量"或涉及多个文件时，调用 batchConvertAndZip 工具
                4. 回复必须用中文，简洁清晰
                5. 转换完成后，告知文件名、下载链接
                6. 对于 JSON/CSV 结果，提供简短的预览内容
                """);

        if (fileIds != null && !fileIds.isEmpty()) {
            prompt.append("\n当前已上传的文件：\n");
            for (String fileId : fileIds) {
                fileStorageService.get(fileId).ifPresent(info ->
                        prompt.append("- [ID: ").append(info.getId())
                                .append("] ").append(info.getOriginalName())
                                .append(" (").append(formatSize(info.getSize())).append(")\n")
                );
            }
            prompt.append("\n调用工具时使用文件 ID 来引用文件。");
        }

        return prompt.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
