package com.sgdx.aiagent.controller;

import com.sgdx.aiagent.agent.YuManus;
import com.sgdx.aiagent.app.GtApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private GtApp gtApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private @Qualifier("zhiPuAiChatModel") ChatModel zhipuaiChatModel;

    /**
     * 同步调用 AI 高套核查应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/gt_app/chat/sync")
    public String doChatWithGtAppSync(String message, String chatId) {
        return gtApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 高套核查应用
     *
     * @param message
     * @param chatId
     * @return
     */
    /**
     * SSE 流式调用 AI 高套核查应用
     *
     * 该接口使用 Server-Sent Events (SSE) 技术实现流式响应，
     * 客户端可以实时接收 AI 的回复内容，而不是等待完整响应。
     *
     * @param message 用户发送的消息内容
     * @param chatId 聊天会话ID，用于保持上下文
     * @return Flux<String> 响应式流，包含 AI 的流式回复
     */
    @GetMapping(value = "/gt_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithGtAppSSE(String message, String chatId) {
        // 调用 gtApp 的流式聊天方法，返回响应式数据流
        return gtApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 高套核查应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/gt_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithGtAppServerSentEvent(String message, String chatId) {
        return gtApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 高套核查应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/gt_app/chat/sse_emitter")
    public SseEmitter doChatWithGtAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        gtApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        YuManus yuManus = new YuManus(allTools, zhipuaiChatModel);
        return yuManus.runStream(message);
    }
}
