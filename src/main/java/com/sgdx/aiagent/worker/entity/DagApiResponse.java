package com.sgdx.aiagent.worker.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DagApiResponse<T> {

    private Integer code;
    private String message;
    private Body<T> body;
    private Long timestamp;
    private String serial;

    public boolean isSuccess() {
        return code != null && code == 0;
    }

    @Data
    public static class Body<T> {
        private T data;
        private Page page;
    }

    @Data
    public static class Page {
        @JsonProperty("index")
        private Integer index;
        private Integer size;
        private Long total;
    }
}