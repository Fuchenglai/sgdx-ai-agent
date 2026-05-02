package com.sgdx.aiagent.worker.job.cycle;


import com.sgdx.aiagent.worker.manager.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CdapJob {

    @Autowired
    private PlaywrightManager playwrightManager;

    @Scheduled(initialDelay = 3*60*1000, fixedRate = 17* 60 * 1000)
    public void monitorDuty() {
        playwrightManager.reTryProcess(List.of("高套受理时报",
                "值班流程",
                "用户销售品资料表",
                "用户基础资料表（日）",
                "CRM工号及揽装表",
                "人力信息表",
                "用户销售品模型",
                "省积分销售额"
        ));
    }
}
