package com.sgdx.aiagent.worker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeChatBotService {
    @Value("${wechat.bot.webhook}")
    private String webhookUrl;

    @Resource
    private RestTemplate restTemplate;

    /**
     * 发送文本消息
     * @param content 消息内容
     * @param mentionedList 要@的成员的userid列表，传null或空列表则不@。想@所有人传["@all"]
     */
    public void sendTextMessage(String content, List<String> mentionedList) {
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "text");

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", content);
        if (mentionedList != null && !mentionedList.isEmpty()) {
            textContent.put("mentioned_list", mentionedList);
        }
        body.put("text", textContent);

        sendPostRequest(body);
    }

    /**
     * 发送Markdown消息
     * @param content Markdown格式的消息内容
     */
    public void sendMarkdownMessage(String content) {
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");

        Map<String, Object> markdownContent = new HashMap<>();
        markdownContent.put("content", content);
        body.put("markdown", markdownContent);

        sendPostRequest(body);
    }

    private void sendPostRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForObject(webhookUrl, request, String.class);
    }
}
