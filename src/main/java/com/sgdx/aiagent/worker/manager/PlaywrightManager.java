package com.sgdx.aiagent.worker.manager;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
// 临时注释掉，暂时不需要数据库功能
// import com.sgdx.aiagent.worker.entity.CookieEntity;
// import com.sgdx.aiagent.worker.service.CookieService;
import com.sgdx.aiagent.worker.entity.NodeInstance;
import com.sgdx.aiagent.worker.service.ExtractDagNodeService;
import com.sgdx.aiagent.worker.service.WeChatBotService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Playwright管理器
 * Spring管理的单例Bean，在应用启动时自动初始化Playwright实例
 * 支持4个求职平台的共享BrowserContext和登录状态监控
 * 所有平台在同一个浏览器窗口的不同标签页中运行
 */
@Slf4j
@Getter
@Component
@Lazy
public class PlaywrightManager {

    // Playwright实例
    private Playwright playwright;

    // 浏览器实例（所有平台共享）
    private Browser browser;

    // 浏览器上下文（所有平台共享，在同一个窗口中打开多个标签页）
    private BrowserContext context;

    // 猎聘页面
    private Page liepinPage;

    //cdap页面
    private Page cdapPage;

    //数据开发页面
    private Page analysePage;


    // 登录状态追踪（平台 -> 是否已登录）
    private final Map<String, Boolean> loginStatusMap = new ConcurrentHashMap<>();

    //值班流程节点的重试次数
    private Map<String, Integer> dutyNodeRetryTimes = new HashMap<>();

    private final List<String> targetNodes = List.of();

    // 登录状态监听器
    private final List<Consumer<LoginStatusChange>> loginStatusListeners = new CopyOnWriteArrayList<>();


    // 控制是否暂停对liepinPage的后台监控
    private volatile boolean liepinMonitoringPaused = false;

    // 控制是否暂停对cdapPage的后台监控
    private volatile boolean cdapMonitoringPaused = false;


    // 默认超时时间（毫秒）
    private static final int DEFAULT_TIMEOUT = 30000;

    // Playwright调试端口
    private static final int CDP_PORT = 7866;


    // 平台URL常量
    private static final String LIEPIN_URL = "https://www.liepin.com";

    private static final String CDAP_HOME_URL = "http://132.122.113.148:19001/atomicportal/#/home?menuId=1";

    private static final String CDAP_LOGIN_URL = "http://132.122.113.148:19001/atomicportal/#/login";

    private static final String CDAP_DATA_CONSOLE_URL = "https://132.121.108.31:24102/index.html?param=Y2ZLNkRNcHk2M25KNElIRXJMM1FCVzdZY3BqZXJxTVQwSzJJWHBqUUsyajJseW11NEF4M1ZTb0lRcXhQMDZNQjRqbGlsVXVHbFZzdHBLbGZpdkloSzhvY1UzYVYvYzVRODlCQWRzU3U0MUwyMDJnSDVleC96M0RqN2t6a3lwenNhd2hwaXlmZU1lam9xdmtydTBIblM2Y0lST0NiRFZHVnBFeE01YW1tR3JVL28xZGdyVy80c0VmTGhzdGtXTGJYYzhoQklsdFhrVzhtOXhjTmIrbXFyWG5Mc2wrQndmaHRHRnMvcVVydDVNWW5zYkVob01vR21kcnR4MC9xanJxNk1xVUFWQXNYM3hDdzlONzh1TGgxK3JCRFdsN1ovVERqSW1WQjh5b3N1emZxWTRIQTNVWWNFSkRONEtKdWdDbmtRWUJpUWhsMUlTYmlIVERJSjh0N3dhNFNHMGJ2Z01aSUZJbnRUTkdVOFdMYTIxamwzZ3YxcFRYZnJUbEw2cUwwYkt6ZHF4M2lOR1pYUUNqd1JTY05TcWJoR2pjcXNWVy9tYXRISndSL0xRdktkSXpIVE5WcThvNGlFZUtVR1BPK256QnZwSjVQdTJtdFNHMkNQZHdPSUxFSjVZQldBZGw0bEw4cTJiZ3V3Y2tiWlU5NEJjME5ndFlnWXBDU2dJSHg=&sign=Y2UxNDk1MGE2NDU4MTFhZjU2MmFkNGE5NGQyZmEwYTA=&homePageUrl=http://132.122.113.148:19001&userName=laifc11#/query_advance_gp";

    private static final String CDAP_PROCESS_MONITOR_URL = "https://132.121.108.31:24102/index.html?param=Sk5Vbmhma3VkdC9vUHN2Ty9nZWdKWStna1VKRFJNMngzY1M2bGhRVGIvSG5ob3l1dC9YWlQyZEJDV0ZlL0FhUHY0dGJyMzZVek0za2JEcjB6S2ZoeFVmYzdWU0JzRURqKytWQlFEUjdzYUk1aHUxbDF1Smt4K3NscTNOTGl1OVN3b3RnZEwrZm01STZZa1ZqQjVTdzdTVUErQVQ0MTI1NzZnd0Z0dmFvZjc4U3I1MGt2RWNSYnMzUWRyL0M4Nnp0Vm9XbGpxLzVUVHhmVDlBU1g3L2hkWktmTGw5S1FTcnFHek51T0dReExINHNpRDFQc084K2J4dHNlMTdSSDNBZFM5cjczTHYrUE9YdWsyWmdhanlyWlRMNzhGQVk3TDByYjhDUkdxcGR1WjZmNHBjTmFLY1lkRlVlOTVvcmJwQUpqZkQzMW5CYkswZWEvTTJuaktNeTBTUFY5MUZMSk9mMXdra01KV0dRbjJ5bDNPUVZicnAydC83eEt5ZzZueEV1RUJzRGphbUdGMTQ3eVVta3E2OVhKZVl0OVMwQlVaeFVpa1l2ZVBzRjQ2eHNoVENRK2srMmw5cmM2eTRFTWhNOW9sVTRHRC84OE9SaHcwRlZQZzF5VURIL3QzRGdFMmNpQnQyZ3liNlVnQXZTWmhxSC9xMHpuZjVoZkRsVXJjQjQ=&sign=YTk5YjI3OWRkOWFlMWFmMjA5MmEyMjRkZWYwMDU2YjE=&homePageUrl=http://132.122.113.148:19001&userName=laifc11#/tasks_list";

    // 临时注释掉，暂时不需要数据库功能
    // @Autowired
    // private CookieService cookieService;

    @Autowired
    private ExtractDagNodeService extractDagNodeService;

    @Resource
    private WeChatBotService weChatBotService;

    /**
     * 初始化Playwright实例（延迟初始化）
     */
    public void init() {
        if (isInitialized()) {
            return;
        }
        log.info("========================================");
        log.info("  初始化浏览器自动化引擎");
        log.info("========================================");

        try {
            // 启动Playwright
            playwright = Playwright.create();
            log.info("✓ Playwright引擎已启动");

            // 创建浏览器实例，使用固定CDP端口7866，最大化启动
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // 非无头模式，可视化调试
                    .setSlowMo(1000) // 放慢操作速度，便于调试
                    .setArgs(List.of(
                            "--remote-debugging-port=" + CDP_PORT, // 使用固定CDP端口
                            "--start-maximized", // 最大化启动窗口

                            "--disable-web-security", // 禁用Web安全策略
                            "--disable-features=IsolateOrigins,site-per-process" // 禁用站点隔离
                    )));
            log.info("✓ Chrome浏览器已启动 (调试端口: {})", CDP_PORT);

            // 创建共享的BrowserContext（所有平台在同一个窗口的不同标签页中）
            context = browser.newContext(new Browser.NewContextOptions()
                    .setIgnoreHTTPSErrors(true) // 忽略HTTPS错误
                    .setViewportSize(null) // 不设置固定视口，使用浏览器窗口实际大小
                    .setUserAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"));
            log.info("✓ BrowserContext已创建（所有平台共享）");

            // 顺序创建所有Page（避免并发创建Page导致的竞态条件）
            /*liepinPage = context.newPage();
            liepinPage.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("✓ 猎聘 Page已创建");*/

            cdapPage = context.newPage();
            cdapPage.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("✓ cdap Page已创建");

            // 并发执行各平台的初始化逻辑（导航、Cookie加载等）
            log.info("开始并发初始化所有平台...");
            //CompletableFuture<Void> liepinFuture = CompletableFuture.runAsync(this::setupLiepinPlatform);
            CompletableFuture<Void> cdapFuture = CompletableFuture.runAsync(this::setupCdapPlatform);

            // 等待所有平台初始化完成
            CompletableFuture.allOf(cdapFuture).join();
            log.info("✓ 浏览器自动化引擎初始化完成（所有平台已并发启动）");
            log.info("========================================");

            try {
                log.info("延迟2min后，开始第一次登录后的流程监控任务...");
                Thread.sleep(2 * 60 * 1000);
                cdapPage.locator("p.name[data-v-252ac23d]:has-text('自助分析')").click();
                Thread.sleep(3 * 1000);
                analysePage = cdapPage.waitForPopup(() -> {
                    cdapPage.locator("p.name[data-v-6cb72f89]:has-text('地市专区')").click();
                });
                Thread.sleep(3 * 1000);
                int maxRetries = 3;
                boolean navigateSuccess = false;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        //点击查看按钮
                        analysePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("查看")).click();
                        Thread.sleep(2 * 1000);
                        navigateSuccess = true;
                        break;
                    } catch (InterruptedException e) {
                        // 记录中断事件
                        log.error("线程被中断");
                        // 重新设置中断状态
                        Thread.currentThread().interrupt();
                    } catch (PlaywrightException e) {
                        log.error("Playwright执行,点击查看按钮出错", e);
                        if (attempt < maxRetries) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
                if (!navigateSuccess) {
                    log.warn("{}页面导航失败", "analysePage");
                }
                reTryProcess(List.of("值班流程"));
            } catch (Exception e) {
                log.error("第一次登录的流程监控任务执行异常", e);
            }

        } catch (Exception e) {
            log.error("✗ 浏览器自动化引擎初始化失败", e);
            throw new RuntimeException("Playwright初始化失败", e);
        }
    }

    public void hiveSqlQuery() {
        try {
            cdapPage.locator("p.name[data-v-252ac23d]:has-text('自助分析')").click();
            Page sqlQueryPage = cdapPage.waitForPopup(() -> {
                cdapPage.locator("p.name[data-v-6cb72f89]:has-text('地市专区')").click();
            });
            Thread.sleep(3 * 1000);
            //点击查看按钮
            sqlQueryPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("查看")).first().click();
            Thread.sleep(2 * 1000);

            sqlQueryPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("仓库管理")).click();
            sqlQueryPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(" 数据控制台")).click();
            sqlQueryPage.locator(".CodeMirror-scroll").click();
            sqlQueryPage.getByRole(AriaRole.TEXTBOX).nth(2).fill("select * from dwd_mboss_ods_serv limit 10;");


            // 使用 waitForResponse 更加简洁
            Response apiResponse = sqlQueryPage.waitForResponse(
                    //todo url应该为真实路径
                    resp -> resp.url().contains("/api/data") && resp.status() == 200,
                    () -> {
                        // 在这个 Runnable 中触发操作
                        sqlQueryPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("执行").setExact(true)).click();
                    }
            );

            // 此时已经确保响应返回，直接读取
            String body = apiResponse.text();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("获取网络响应超时或失败", e);
        }
    }

    public List<String> reTryProcess(List<String> processes) {
        List<String> passProcesses = new ArrayList<>();
        try {
            analysePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("数据开发")).first().click();
            Thread.sleep(2 * 1000);
            analysePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("流程监控")).first().click();
            Thread.sleep(2 * 1000);
            analysePage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("失败")).click();
            Thread.sleep(4 * 1000);
            for (String process : processes) {
                //自动清空后输入文本
                analysePage.getByPlaceholder("搜索流程").fill(process);
                //点击搜索按钮
                analysePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("")).click();
                Thread.sleep(3 * 1000);

                //如果已经有成功的流程就不再重跑
                if (analysePage.getByText("成功 1").count() > 0 || analysePage.getByText("成功 2").count() > 0 || analysePage.getByText("成功 3").count() > 0) {
                    log.info("流程{}成功了，不再执行失败重跑", process);
                    passProcesses.add(process);
                    continue;

                }
                boolean rowVisible = analysePage.locator(".el-table__fixed-body-wrapper > .el-table__body > tbody > tr > .el-table_5_column_9 > .cell").first().isVisible();

                if (rowVisible) {
                    log.info("流程{}失败了，即将执行失败重跑", process);
                    // 1. 定位到你想要操作的那行的“操作”按钮.(这里使用第一行，如果是其他行，请使用 nth(index))
                    Locator operationBtn = analysePage
                            .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("操作")) // 查找文本为“操作”的按钮
                            .first();

                    // 2. 获取按钮关联的菜单ID
                    String menuId = operationBtn.getAttribute("aria-controls"); // 获取 'aria-controls' 的值，例如 "dropdown-menu-5145"
                    if (menuId == null || !menuId.startsWith("dropdown-menu-")) {
                        throw new RuntimeException("无法获取有效的下拉菜单ID，aria-controls: " + menuId);
                    }
                    log.info("目标 menu ID: " + menuId); // 用于调试

                    // 3. 悬停触发菜单
                    operationBtn.hover();

                    // 4. 使用获取到的具体ID等待菜单可见
                    Locator targetDropdownMenu = analysePage.locator("#" + menuId); // 构造精确的选择器
                    targetDropdownMenu.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

                    // --- 优化点1: 更严格的菜单等待 ---
                    // 等待菜单容器不仅可见，而且稳定 (没有正在进行的动画),这有助于确保内部的菜单项也完成了渲染和动画
                    targetDropdownMenu.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE) // 元素可见
                            .setTimeout(10000)); // 设置一个合理的超时时间，例如 10 秒

                    // --- 优化点2: 尝试等待菜单内部的 *任意* 子元素可见 ---
                    // 有时候，直接等待容器有效，但内部元素需要稍后才稳定
                    // 等待菜单内的一个通用子元素（例如，一个 li 元素）
                    targetDropdownMenu.locator("li").first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(5000)); // 设置一个较短的超时时间

                    //通过DAG按钮判断有哪些节点失败了
                    Locator dagItem = targetDropdownMenu.getByText("DAG").first();
                    dagItem.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE) // 等待该项完全可见
                            .setTimeout(5000)); // 设置一个合理的超时时间
                    Response dagResponse = analysePage.waitForResponse(
                            resp -> resp.url().contains("NodeInstance/QueryActive") && resp.status() == 200,
                            () -> {
                                // 点击DAG按钮
                                dagItem.click();
                            }
                    );

                    // 此时已经确保响应返回，直接读取
                    String body = dagResponse.text();
                    List<NodeInstance> nodes = extractDagNodeService.extractDataQuick(body);
                    // 记录失败节点的重试次数 (SRF = 失败结束)
                    List<NodeInstance> failNodes = nodes.stream()
                            .filter(n -> "SRF".equals(n.getNodeState()))
                            .toList();
                    List<String> threeTimesNodes = new ArrayList<>();
                    for (NodeInstance failNode : failNodes) {
                        Integer times = dutyNodeRetryTimes.getOrDefault(failNode.getNodeNameCn(), 0);
                        if (times >= 2) {
                            threeTimesNodes.add(failNode.getNodeNameCn());
                        }
                        dutyNodeRetryTimes.put(failNode.getNodeNameCn(), times + 1);
                        log.info("节点{}重试次数为：{}", failNode.getNodeNameCn(), times + 1);
                    }
                    if (!threeTimesNodes.isEmpty()) {
                        String s = String.join(", ", threeTimesNodes);
                        weChatBotService.sendTextMessage(process + "失败次数在3次及以上的节点有：" + s + "。请手动进入CDAP查看并处理。", null);
                    }

                    Thread.sleep(2 * 1000);
                    // 关闭弹出的DAG窗口
                    analysePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close")).click();
                    Thread.sleep(3*1000);

                    //如果已经有三次重试失败的节点，则不再执行失败重跑,交给人来手工处理
                    if(!threeTimesNodes.isEmpty()) continue;
                    // 3. 悬停触发菜单
                    operationBtn.hover();
                    // 4. 使用获取到的具体ID等待菜单可见
                    targetDropdownMenu = analysePage.locator("#" + menuId); // 构造精确的选择器
                    targetDropdownMenu.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

                    // --- 优化点1: 更严格的菜单等待 ---
                    targetDropdownMenu.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE) // 元素可见
                            .setTimeout(10000)); // 设置一个合理的超时时间，例如 10 秒

                    // --- 优化点2: 尝试等待菜单内部的 *任意* 子元素可见 ---
                    targetDropdownMenu.locator("li").first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(5000)); // 设置一个较短的超时时间
                    Locator failureRetryItem = targetDropdownMenu.getByText("失败重跑").first();
                    failureRetryItem.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE) // 等待该项完全可见
                            .setTimeout(5000)); // 设置一个合理的超时时间

                    // 5. 点击失败重跑按钮
                    failureRetryItem.click();
                    Thread.sleep(1 * 1000);
                    analysePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("确定")).click();
                }
            }

            //刷新页面，以便下一次执行时不会提示重新登录
            cdapPage.reload(new Page.ReloadOptions().setTimeout(30000));
            return passProcesses;
        } catch (InterruptedException e) {
            // 记录中断事件
            log.error("线程被中断");
            // 重新设置中断状态
            Thread.currentThread().interrupt();
            return passProcesses;
        } catch (PlaywrightException e) {
            log.error("Playwright在执行定时任务检查流程时出错", e);
            return passProcesses;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 设置登录状态监控
     *
     * @param page 页面实例
     */
    private void setupLoginMonitoring(String platform, Page page, boolean monitoringPaused) {
        // 监听页面导航事件，检测URL变化
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                // 事件触发的检查在Playwright内部线程执行，仍需遵守暂停标志
                if (!monitoringPaused) {

                    boolean loggedIn = false;
                    switch (platform) {
                        case "liepin":
                            loggedIn = checkIfLiepinLoggedIn();
                            break;
                        case "cdap":
                            loggedIn = checkIfCdapLoggedIn();
                            break;
                        default:
                            throw new IllegalArgumentException("不支持的平台: " + platform);
                    }
                    log.info("{}平台导航到新页面，检查登录状态为{}", platform, loggedIn);
                    setLoginStatus(platform, loggedIn);
                }
            }
        });

        log.info("{}平台登录状态监控已启用", platform);
    }

    /**
     * 设置cdap平台（加载导航、监控）
     */
    private void setupCdapPlatform() {
        log.info("开始初始化cdap平台（加载导航、监控）");

        //导航到cdap登录页
        navigate2PageByUrl("cdap", CDAP_LOGIN_URL);


        // 初始化登录状态并通知（如果有SSE连接会立即推送），loginStatusMap有<liepin,false>
        setLoginStatus("cdap", false);
        // 设置登录状态监控,URL变化时检查登录状态
        setupLoginMonitoring("cdap", cdapPage, cdapMonitoringPaused);
    }

    /**
     * 设置猎聘平台（加载Cookie、导航、监控）
     */
    private void setupLiepinPlatform() {
        log.info("开始初始化猎聘平台（加载导航、监控）");

        // 临时注释掉，暂时不需要数据库功能（从数据库加载猎聘平台Cookie）
        // // 尝试从数据库加载猎聘平台Cookie到上下文
        // try {
        //     CookieEntity cookieEntity = cookieService.getCookieByPlatform("liepin");
        //     if (cookieEntity != null && cookieEntity.getCookieValue() != null && !cookieEntity.getCookieValue().isBlank()) {
        //         String cookieStr = cookieEntity.getCookieValue();
        //         List<Cookie> cookies = parseCookiesFromString(cookieStr);
        //
        //         if (!cookies.isEmpty()) {
        //             context.addCookies(cookies);
        //             log.info("已从数据库加载猎聘 Cookie并注入浏览器上下文，共 {} 条", cookies.size());
        //         } else {
        //             log.warn("解析猎聘Cookie失败，未能加载任何Cookie");
        //         }
        //     } else {
        //         log.info("数据库未找到猎聘Cookie或值为空，跳过Cookie注入");
        //     }
        // } catch (Exception e) {
        //     log.warn("从数据库加载猎聘Cookie失败: {}", e.getMessage());
        // }

        // 导航到猎聘首页（带重试机制）
        navigate2PageByUrl("liepin", LIEPIN_URL);

        // 初始化登录状态并通知（如果有SSE连接会立即推送），loginStatusMap有<liepin,false>
        setLoginStatus("liepin", checkIfLiepinLoggedIn());
        // 设置登录状态监控,URL变化时检查登录状态
        setupLoginMonitoring("liepin", liepinPage, liepinMonitoringPaused);
    }

    public void navigate2PageByText(String platform, String text) {
        Page myPage = null;
        switch (platform) {
            case "liepin":
                myPage = liepinPage;
                break;
            case "cdap":
                myPage = cdapPage;
                break;
            default:
                throw new IllegalArgumentException("不支持的平台: " + platform);
        }

        if (myPage.getByText(text).isEnabled()) {
            myPage.getByText(text).first().click();
        }
    }

    public void fill(String platform, String locator, String text) {
        Page myPage = null;
        switch (platform) {
            case "liepin":
                myPage = liepinPage;
                break;
            case "cdap":
                myPage = cdapPage;
                break;
            default:
                throw new IllegalArgumentException("不支持的平台: " + platform);
        }

        if (myPage.locator(locator).isEnabled()) {
            log.info("开始在{}平台填充文本：'{}'", platform, text);
            myPage.locator(locator).fill(text);
        }
    }

    public void click(String platform, String locator) {
        Page myPage = null;
        switch (platform) {
            case "liepin":
                myPage = liepinPage;
                break;
            case "cdap":
                myPage = cdapPage;
                break;
            default:
                throw new IllegalArgumentException("不支持的平台: " + platform);
        }
        if (myPage.locator(locator).isEnabled()) {
            log.info("开始在{}平台点击元素：'{}'", platform, locator);
            myPage.locator(locator).click();
        }
    }

    public void navigate2PageByUrl(String platform, String url) {

        Page myPage = null;
        switch (platform) {
            case "liepin":
                myPage = liepinPage;
                break;
            case "cdap":
                myPage = cdapPage;
                break;
            default:
                throw new IllegalArgumentException("不支持的平台: " + platform);
        }

        int maxRetries = 3;
        boolean navigateSuccess = false;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                myPage.navigate(url, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                navigateSuccess = true;
                break;
            } catch (Exception e) {
                // Playwright在并发导航时可能抛出 "Object doesn't exist" 异常，但页面实际已加载
                boolean pageAccessible = false;
                try {
                    String curUrl = myPage.url();
                    pageAccessible = curUrl != null && curUrl.contains("#/home");
                } catch (Exception ignored) {
                }

                if (pageAccessible) {
                    navigateSuccess = true;
                    break;
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (!navigateSuccess) {
            log.warn("{}页面导航失败", platform);
        }
    }

    /**
     * 检查猎聘是否已登录
     * 已登录：能找到用户头像 <img class="header-quick-menu-user-photo" ...>
     * 未登录：能找到 <span id="header-quick-menu-login">登录/注册</span>
     */
    private boolean checkIfLiepinLoggedIn() {
        try {
            // 先检查“登录/注册”入口是否可见，若可见则明确未登录
            try {
                Locator loginEntry = liepinPage.locator(
                        "#header-quick-menu-login, a[href*='login'], a[data-key='login'], button[data-key='login'], text=/登录|注册/").first();
                if (loginEntry.isVisible()) {
                    log.info("检测到未登录猎聘，保持在登录页或首页等待扫码登录");
                    // 若不在登录页，则导航到登录页并尝试切换二维码
                    String currentUrl = null;
                    try {
                        currentUrl = liepinPage.url();
                    } catch (Exception ignored) {
                    }
                    try {
                        if (currentUrl == null || !currentUrl.contains("/login")) {
                            liepinPage.navigate("https://www.liepin.com/login");
                            try {
                                Thread.sleep(800);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        // 优先点击官方切换二维码的容器
                        Locator qrSwitch = liepinPage.locator(".switch-type-mask-img-box").first();
                        if (qrSwitch.isVisible()) {
                            qrSwitch.click();
                            log.info("已切换到猎聘二维码登录页面，等待用户扫码...");
                        } else {
                            // 兼容新版页面：图片资源名包含 qrcode-btn，需要点击其父级按钮
                            Locator qrImg = liepinPage.locator("img[src*='qrcode-btn']").first();
                            if (qrImg.count() > 0 && qrImg.isVisible()) {
                                try {
                                    // 尝试点击父节点或最近的可点击容器
                                    qrImg.click();
                                } catch (Exception ignored) {
                                    try {
                                        Locator parentBtn = qrImg.locator("xpath=ancestor::button[1] | xpath=ancestor::*[contains(@class,'btn')][1]").first();
                                        if (parentBtn.count() > 0 && parentBtn.isVisible()) {
                                            parentBtn.click();
                                        }
                                    } catch (Exception ignored2) {
                                    }
                                }
                                log.info("已通过二维码按钮切换到扫码登录状态");
                            }
                        }
                    } catch (Exception e) {
                        log.debug("猎聘登录页引导/二维码切换失败: {}", e.getMessage());
                    }
                    return false;
                }
            } catch (Exception ignored) {
            }

            // 再检查已登录特征：用户信息容器或用户头像是否存在（无需强制可见）
            try {
                if (liepinPage.locator("#header-quick-menu-user-info").count() > 0) {
                    log.debug("猎聘登录检测：存在用户信息容器，判定已登录");
                    return true;
                }
            } catch (Exception ignored) {
            }

            try {
                if (liepinPage.locator("img.header-quick-menu-user-photo, .header-quick-menu-user-photo").count() > 0) {
                    log.debug("猎聘登录检测：存在用户头像元素，判定已登录");
                    return true;
                }
            } catch (Exception ignored) {
            }

            // 兜底：若不存在登录入口且也未找到明确已登录特征，按已登录处理（避免误判）
            try {
                boolean loginEntryExists = liepinPage.locator("#header-quick-menu-login, a[href*='login']").count() > 0;
                if (!loginEntryExists) {
                    log.info("猎聘登录检测：未发现登录入口，兜底判定为已登录");
                    return true;
                }
            } catch (Exception ignored) {
            }

            // 默认未登录
            log.debug("猎聘登录检测：未匹配到明确特征，判定未登录");
            return false;
        } catch (Exception e) {
            log.debug("猎聘登录检测异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查cdap是否已登录
     */
    private boolean checkIfCdapLoggedIn() {


        // todo: 完善登录检测逻辑

        try {
            // 先检查“登录/注册”入口是否可见，若可见则明确未登录
            try {
                Locator loginEntry = cdapPage.locator(
                        "button.login-btn, text=/登录/,text=/系统登录/").first();
                if (loginEntry.isVisible()) {
                    log.info("检测到未登录cdap，保持在登录页");
                    // 若不在登录页，则导航到登录页
                    String currentUrl = null;
                    try {
                        currentUrl = cdapPage.url();
                    } catch (Exception ignored) {
                    }
                    try {
                        if (currentUrl == null || !currentUrl.contains("/login")) {
                            cdapPage.navigate(CDAP_LOGIN_URL);
                            try {
                                Thread.sleep(800);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    } catch (Exception e) {
                        log.debug("cdap登录页引导失败: {}", e.getMessage());
                    }
                    return false;
                }
            } catch (Exception ignored) {
            }

            // 再检查已登录特征：用户信息容器或用户头像是否存在（无需强制可见）
            try {
                //todo 应该是analysePage.getByRole(AriaRole.IMG).count() > 0
                if (cdapPage.locator("div.el-dropdown,span.el-popover_reference").count() > 0 || cdapPage.getByRole(AriaRole.IMG).count() > 0) {
                    log.debug("cdap登录检测：存在用户信息容器，判定已登录");

                    //如果是第一次登录，则执行一次流程监控任务
                    if (!loginStatusMap.containsKey("cdap") || loginStatusMap.get("cdap").equals(false)) {
                        // 开启一个异步线程，延迟 5 秒后执行
                       /* CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                                .execute(() -> {

                                });*/
                    }
                    return true;
                }
            } catch (Exception ignored) {
            }

            // 兜底：若不存在登录入口且也未找到明确已登录特征，按已登录处理（避免误判）
            try {
                boolean loginEntryExists = cdapPage.locator("button.login-btn, a[href*='login']").count() > 0;
                if (!loginEntryExists) {
                    log.info("cdap登录检测：未发现登录入口，兜底判定为已登录");
                    return true;
                }
            } catch (Exception ignored) {
            }

            // 默认未登录
            log.debug("cdap登录检测：未匹配到明确特征，判定未登录");
            return false;
        } catch (Exception e) {
            log.debug("cdap登录检测异常: {}", e.getMessage());
            return false;
        }
    }


    /**
     * 统一按平台保存 Cookie 到数据库
     *
     * @param platform 平台标识（boss/liepin/51job/zhilian）
     * @param remark   备注
     */
    public void saveCookiesToDb(String platform, String remark) {
        switch (platform) {
            case "liepin" -> saveLiepinCookiesToDatabase(remark);
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }


    /**
     * 保存猎聘Cookie到数据库
     *
     * @param remark 备注信息
     */
    private void saveLiepinCookiesToDatabase(String remark) {
        // 临时注释掉，暂时不需要数据库功能
        // try {
        //     List<Cookie> cookies = context.cookies();
        //     // 使用ObjectMapper序列化为JSON字符串
        //     String cookieJson = new ObjectMapper().writeValueAsString(cookies);
        //     boolean result = cookieService.saveOrUpdateCookie("liepin", cookieJson, remark);
        //     if (result) {
        //         log.info("保存猎聘Cookie成功，共 {} 条，remark={}", cookies.size(), remark);
        //     }
        // } catch (Exception e) {
        //     log.warn("保存猎聘Cookie失败: {}", e.getMessage());
        // }
        log.info("数据库功能已禁用，跳过保存猎聘Cookie到数据库");
    }

    /**
     * 暂停猎聘页面的后台登录监控（避免与业务流程并发操作页面）
     */
    public void pauseLiepinMonitoring() {
        liepinMonitoringPaused = true;
        log.debug("猎聘登录监控已暂停");
    }


    /**
     * 定时检查登录状态（每30秒）
     * 用于捕获通过DOM元素判断登录状态的场景（无导航也可触发）
     */
    @Scheduled(fixedDelay = 3000 * 10)
    public void scheduledLoginCheck() {
        try {
            if (liepinPage != null && !liepinMonitoringPaused) {
                //checkLiepinLoginStatus(liepinPage);
            }
            // 其他平台如需也可启用（保留，但不强制）

        } catch (Exception e) {
            log.debug("定时登录检测异常: {}", e.getMessage());
        }
    }


    /**
     * 关闭Playwright实例
     * 在Spring容器销毁前自动执行
     */
    @PreDestroy
    public void destroy() {
        log.info("开始关闭Playwright管理器...");

        try {
            if (liepinPage != null) {
                liepinPage.close();
                log.info("猎聘页面已关闭");
            }

            // 关闭共享的BrowserContext
            if (context != null) {
                context.close();
                log.info("共享BrowserContext已关闭");
            }

            // 关闭浏览器
            if (browser != null) {
                browser.close();
                log.info("浏览器已关闭");
            }
            if (playwright != null) {
                playwright.close();
                log.info("Playwright实例已关闭");
            }

            log.info("Playwright管理器关闭完成！");
        } catch (Exception e) {
            log.error("关闭Playwright管理器时发生错误", e);
        }
    }

    /**
     * 检查Playwright是否已初始化
     */
    public boolean isInitialized() {
        return playwright != null && browser != null;
    }

    /**
     * 获取CDP端口号
     */
    public int getCdpPort() {
        return CDP_PORT;
    }

    /**
     * 注册登录状态监听器
     *
     * @param listener 监听器
     */
    public void addLoginStatusListener(Consumer<LoginStatusChange> listener) {
        loginStatusListeners.add(listener);
    }

    /**
     * 移除登录状态监听器
     *
     * @param listener 监听器
     */
    public void removeLoginStatusListener(Consumer<LoginStatusChange> listener) {
        loginStatusListeners.remove(listener);
    }

    /**
     * 手动设置平台登录状态（会触发SSE通知）
     *
     * @param platform   平台名称
     * @param isLoggedIn 是否已登录
     */
    public void setLoginStatus(String platform, boolean isLoggedIn) {
        Boolean previousStatus = loginStatusMap.get(platform);

        // 只有状态真正发生变化时才更新和通知
        if (previousStatus == null || previousStatus != isLoggedIn) {
            loginStatusMap.put(platform, isLoggedIn);

            // 通知所有监听器（触发SSE推送）
            LoginStatusChange change = new LoginStatusChange(platform, isLoggedIn, System.currentTimeMillis());
            loginStatusListeners.forEach(listener -> {
                try {
                    listener.accept(change);
                } catch (Exception e) {
                    log.error("通知登录状态监听器失败: platform={}, isLoggedIn={}", platform, isLoggedIn, e);
                }
            });

//            log.info("登录状态已更新: platform={}, isLoggedIn={}", platform, isLoggedIn);
        }
    }

    /**
     * 从JSON字符串解析Cookie列表
     *
     * @param cookieJson Cookie的JSON字符串
     * @return Cookie列表
     */
    private List<Cookie> parseCookiesFromString(String cookieJson) {
        List<Cookie> cookies = new ArrayList<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonArray = objectMapper.readTree(cookieJson);

            for (com.fasterxml.jackson.databind.JsonNode node : jsonArray) {
                // 创建Cookie对象（name和value是必需的）
                Cookie cookie = new Cookie(
                        node.get("name").asText(),
                        node.get("value").asText()
                );

                // 设置可选字段
                if (node.has("domain") && !node.get("domain").isNull()) {
                    cookie.domain = node.get("domain").asText();
                }
                if (node.has("path") && !node.get("path").isNull()) {
                    cookie.path = node.get("path").asText();
                }
                if (node.has("expires") && !node.get("expires").isNull()) {
                    cookie.expires = node.get("expires").asDouble();
                }
                if (node.has("httpOnly") && !node.get("httpOnly").isNull()) {
                    cookie.httpOnly = node.get("httpOnly").asBoolean();
                }
                if (node.has("secure") && !node.get("secure").isNull()) {
                    cookie.secure = node.get("secure").asBoolean();
                }
                if (node.has("sameSite") && !node.get("sameSite").isNull()) {
                    String sameSite = node.get("sameSite").asText();
                    if (sameSite != null && !sameSite.isEmpty()) {
                        cookie.sameSite = com.microsoft.playwright.options.SameSiteAttribute.valueOf(
                                sameSite.toUpperCase()
                        );
                    }
                }

                cookies.add(cookie);
            }

            log.debug("成功解析Cookie，共 {} 条", cookies.size());
        } catch (Exception e) {
            log.error("解析Cookie JSON失败: {}", e.getMessage(), e);
        }

        return cookies;
    }

    /**
     * LoginStatusChange - 登录状态变化DTO
     */
    public record LoginStatusChange(String platform, boolean isLoggedIn, long timestamp) {
    }
}
