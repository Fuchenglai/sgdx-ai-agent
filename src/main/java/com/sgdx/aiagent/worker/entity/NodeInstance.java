package com.sgdx.aiagent.worker.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class NodeInstance {

    private Long nodeInstanceId;
    private String batchNo;
    private String batchExt;
    private Integer nodeTypeId;
    private Long nodeId;
    private Long tenantId;
    private Long projectId;
    private String nodeName;
    private String nodeNameCn;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime nodeStateTime;

    private String scheduleCycle;
    private String dependenciesOn;
    /* "nodeState": "S0S" 成功结束
            "nodeState": "SRF" 失败结束
            "nodeState": "S0A" 未运行
            * */
    private String nodeState;
    private String recordState;
    private Integer createId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime createTime;

    private Integer modifierId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime modifyTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime nodeStartTime;

    private Long parentNodeInstanceId;
    private String taskInfo;
    private String creator;
    private String modifier;
}