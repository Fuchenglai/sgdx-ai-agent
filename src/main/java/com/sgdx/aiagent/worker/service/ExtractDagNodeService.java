package com.sgdx.aiagent.worker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgdx.aiagent.worker.entity.NodeInstance;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExtractDagNodeService {

    private final ObjectMapper objectMapper;

    public ExtractDagNodeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<NodeInstance> extractDataQuick(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        // 先校验
        if (root.path("code").asInt(-1) != 0) {
            throw new RuntimeException("接口调用失败");
        }

        // 直接取 data 节点反序列化
        JsonNode dataNode = root.path("body").path("data");
        return objectMapper.readValue(
                dataNode.traverse(),
                new TypeReference<List<NodeInstance>>() {}
        );
    }
}
