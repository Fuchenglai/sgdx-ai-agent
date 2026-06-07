package com.sgdx.aiagent.app;

import com.sgdx.aiagent.advisor.MyLoggerAdvisor;
import com.sgdx.aiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class GtApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "扮演高套核查领域的专家。开场向用户表明身份，告知用户可反馈高套异常数据或者有疑问高套规则。" +
            "围绕新入网，存量两种高套提问：新入网高套询问新入网宽带，新入网融合专线，单品专线，后付费移动单品，智家类高套，政企团购；" +
            "存量高套询问专线升级，改套餐，强合约" +
            "引导用户讲述是什么问题，如局向问题，积分问题，揽装人问题，高套类型问题等";

    /**
     * 初始化 ChatClient
     *
     * @param zhipuaiChatModel
     */
    public GtApp(@Qualifier("zhiPuAiChatModel") ChatModel zhipuaiChatModel) {
//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(zhipuaiChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        log.info("doChatByStream方法打印"+"message: {},chatId：{}", message,chatId);
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    record GtReport(String title, List<String> suggestions) {

    }

    /**
     * AI 高套报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public GtReport doChatWithReport(String message, String chatId) {
        GtReport gtReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成高套结果，标题为{用户名}的高套报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(GtReport.class);
        log.info("gtReport: {}", gtReport);
        return gtReport;
    }


    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
//                .advisors(new QuestionAnswerAdvisor(gtAppVectorStore))
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(gtAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        gtAppRagCustomAdvisorFactory.createGtAppRagCustomAdvisor(
////                                gtAppVectorStore, "存量"
////                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;


    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

}
