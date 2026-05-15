package com.sgdx.aiagent.worker.job.cycle;


import com.sgdx.aiagent.worker.manager.PlaywrightManager;
import com.sgdx.aiagent.worker.service.WeChatBotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CdapJob {

    @Autowired
    private PlaywrightManager playwrightManager;

    @Resource
    private WeChatBotService weChatBotService;

    // 初始待监控列表（不可变，作为数据源）
    private static final List<String> INITIAL_PROCESSES = List.of(
            "值班流程",
            "用户销售品资料表",
            "用户基础资料表（日）",
            "CRM工号及揽装表",
            "人力信息表",
            "用户销售品模型",
            "省积分销售额"
    );

    // 实际运行中的可变列表（每次执行后会被修改）
    private List<String> activeProcesses = new ArrayList<>(INITIAL_PROCESSES);

    @Scheduled(initialDelay = 15 * 60 * 1000, fixedDelay = 15 * 60 * 1000)
    public void monitorDuty() {
        log.info("开始执行定时任务检查流程，当前时间：{}", LocalDateTime.now());
        if (activeProcesses.isEmpty()) {
            log.info("所有流程已处理完毕，无需执行");
            return;
        }
        List<String> res = playwrightManager.reTryProcess(activeProcesses);
        // 2. 剔除已成功处理的元素
        if (res != null && !res.isEmpty()) {
            activeProcesses.removeIf(res::contains);
            if (activeProcesses.isEmpty()) {
                String content = "所有关注流程都已经运行成功！";
                weChatBotService.sendTextMessage(content, null);
            }
        }

    }

    /*@Scheduled(fixedDelay = 120 * 60 * 1000)
    public void sendDailyNotice() {
        String content = "【测试信息】打印测试信息。@赖富城";
        weChatBotService.sendTextMessage(content, List.of("19928554196"));
    }*/
}
