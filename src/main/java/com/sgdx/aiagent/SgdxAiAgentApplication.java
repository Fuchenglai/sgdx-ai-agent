package com.sgdx.aiagent;

// 临时注释掉，暂时不需要数据库功能
// import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})

// 临时注释掉，暂时不需要数据库功能
// @MapperScan("com.sgdx.aiagent.worker.mapper")
@EnableScheduling
public class SgdxAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SgdxAiAgentApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
