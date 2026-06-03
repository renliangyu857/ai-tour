package com.tourism.rag.service;

import com.tourism.rag.entity.Attraction;
import com.tourism.rag.entity.City;
import com.tourism.rag.helper.DocumentLoaderHelper;
import com.tourism.rag.repository.AttractionRepository;
import com.tourism.rag.repository.CityRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 数据摄入 Service
 *
 * 处理流程：
 * 1. 加载文档（文件/文本/数据库）→ Document
 * 2. Recursive Splitting（chunkSize ~700，overlap 150）→ List<TextSegment>
 * 3. Embedding（Qwen text-embedding-v3）→ List<Embedding>
 * 4. 写入 Milvus（携带 metadata）
 * 5. 更新 MySQL city 表状态
 *
 * 【多城市扩展方法】
 * 新增城市（以北京为例）：
 * 1. 在 MySQL city 表插入：INSERT INTO city (code, name_cn, ...) VALUES ('beijing', '北京', ...)
 * 2. 准备知识库文档（markdown / pdf）
 * 3. 调用 POST /api/ingest/city {cityCode: "beijing", sourceType: "FILE", filePath: "..."}
 * 无需修改任何代码！
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentLoaderHelper documentLoaderHelper;
    private final CityRepository cityRepository;
    private final AttractionRepository attractionRepository;

    @Value("${rag.retrieval.chunk-size:700}")
    private int chunkSize;

    @Value("${rag.retrieval.chunk-overlap:150}")
    private int chunkOverlap;

    // ============================================================
    // 青岛内置知识库（硬编码示例数据）
    // 生产环境替换为从文件/数据库加载
    // ============================================================

    /**
     * 初始化青岛知识库（含预设数据）
     * 接口：POST /api/ingest/qingdao
     */
    @Async
    @Transactional
    public CompletableFuture<Integer> ingestQingdaoData() {
        log.info("开始摄入青岛旅游知识库...");

        List<Document> documents = buildQingdaoDocuments();

        // 同时写入 MySQL 景点数据
        initQingdaoAttractions();

        int count = ingestDocuments(documents);

        // 更新城市状态
        cityRepository.findByCode("qingdao").ifPresent(city -> {
            city.setKnowledgeIngested(true);
            city.setEnabled(true);
            cityRepository.save(city);
        });

        log.info("青岛知识库摄入完成，共处理 {} 个文档块", count);
        return CompletableFuture.completedFuture(count);
    }

    /**
     * 从本地文件摄入指定城市数据
     */
    @Async
    public CompletableFuture<Integer> ingestFromFile(String cityCode, String category, Path filePath) {
        log.info("从文件摄入 - 城市: {}, 分类: {}, 文件: {}", cityCode, category, filePath);
        Document doc = documentLoaderHelper.loadFromFile(filePath, cityCode, category);
        int count = ingestDocuments(List.of(doc));
        updateCityStatus(cityCode);
        return CompletableFuture.completedFuture(count);
    }

    /**
     * 从 MySQL attraction 表向量化摄入
     * 用于将结构化数据转为语义可检索的向量
     */
    @Async
    public CompletableFuture<Integer> ingestFromDatabase(String cityCode) {
        List<Attraction> attractions = attractionRepository.findByCityCode(cityCode);
        log.info("从数据库摄入 - 城市: {}, 景点数量: {}", cityCode, attractions.size());

        List<Document> documents = attractions.stream().map(a -> {
            Map<String, Object> record = new HashMap<>();
            record.put("id", a.getId());
            record.put("name", a.getName());
            record.put("category", a.getCategory());
            record.put("description", a.getDescription());
            record.put("address", a.getAddress());
            record.put("ticketPrice", a.getTicketPrice());
            record.put("openingHours", a.getOpeningHours());
            record.put("seasons", a.getSeasons());
            return documentLoaderHelper.createFromDatabaseRecord(record, cityCode);
        }).toList();

        int count = ingestDocuments(documents);
        updateCityStatus(cityCode);
        return CompletableFuture.completedFuture(count);
    }

    // ============================================================
    // 核心摄入逻辑
    // ============================================================

    /**
     * 通用文档摄入方法
     * 分块 → Embedding → 写入 Milvus
     *
     * @return 写入的文档块数量
     */
    private int ingestDocuments(List<Document> documents) {
        // Recursive Character Text Splitter
        // 优先在段落(\n\n)、句子(\n)、标点处断开，保持语义完整性
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);

        List<TextSegment> allSegments = new ArrayList<>();
        for (Document doc : documents) {
            List<TextSegment> segments = splitter.split(doc);
            log.debug("文档分块完成: {} 个 chunks，metadata: {}",
                    segments.size(), doc.metadata().toMap());
            allSegments.addAll(segments);
        }

        if (allSegments.isEmpty()) {
            log.warn("没有可摄入的文档块");
            return 0;
        }

        // 批量 Embedding（避免单次请求过多 tokens）
        // 每批 32 个 chunk（根据 DashScope API 限制调整）
        int batchSize = 32;
        int totalIngested = 0;

        for (int i = 0; i < allSegments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allSegments.size());
            List<TextSegment> batch = allSegments.subList(i, end);

            // 调用 Qwen Embedding API
            List<Embedding> embeddings = embeddingModel.embedAll(batch).content();

            // 写入 Milvus（metadata 会自动携带 city/category/source 等字段）
            embeddingStore.addAll(embeddings, batch);

            totalIngested += batch.size();
            log.debug("已写入 {}/{} 个文档块到 Milvus", totalIngested, allSegments.size());
        }

        return totalIngested;
    }

    private void updateCityStatus(String cityCode) {
        cityRepository.findByCode(cityCode).ifPresent(city -> {
            city.setKnowledgeIngested(true);
            city.setEnabled(true);
            cityRepository.save(city);
        });
    }

    // ============================================================
    // 青岛知识库示例数据
    // 实际生产建议从 resources/data/qingdao_knowledge.md 加载
    // ============================================================

    private List<Document> buildQingdaoDocuments() {
        List<Document> docs = new ArrayList<>();

        // ---- 景点类 ----
        docs.add(documentLoaderHelper.createFromText("""
                【青岛栈桥】
                青岛栈桥位于青岛市市南区海边，是青岛的标志性景点和城市象征。
                栈桥全长440米，宽8米，始建于1891年，是青岛最早的军事专用码头。
                桥南端建有回澜阁，内设展厅展示青岛历史。每逢节假日，这里是拍摄青岛海景的绝佳位置。

                实用信息：
                - 门票：免费开放
                - 开放时间：全天（回澜阁 8:30-17:00）
                - 地址：青岛市市南区太平路22号
                - 最佳游览时间：清晨日出时分，光线柔和，海鸥群集
                - 周边：步行可达中山路商业街、劈柴院小吃街
                - 交通：公交2路、6路、26路、202路到栈桥站

                亲子游贴士：孩子可以在防波堤上喂海鸥，购买鸡心面包（1元/个）投喂，画面极具感染力。
                摄影贴士：冬季（12月-2月）海鸥最多，是摄影圣地。
                """,
                "qingdao", "attraction", "青岛旅游官网-栈桥", "all"));

        docs.add(documentLoaderHelper.createFromText("""
                【青岛八大关景区】
                八大关景区位于青岛市南区，因区内有八条以关隘命名的道路而得名（韶关路、嘉峪关路等）。
                景区内汇集了来自20多个国家的各式建筑风格，有"万国建筑博览会"之称。
                这里也是青岛最浪漫的街区，法国梧桐、雪松、紫薇等30余种稀有树种构成四季不同景色。

                著名建筑：
                - 花石楼（欧式哥特风格，俯瞰汇泉湾）
                - 公主楼（丹麦式风格，蓝色童话小屋）
                - 元帅楼（西班牙风格）

                实用信息：
                - 门票：景区免费，花石楼等单独购票（约20元）
                - 开放时间：全天
                - 最佳季节：春季（4-5月，碧桃、紫藤花开）；秋季（10-11月，银杏金黄）
                - 地址：青岛市市南区山海关路1号

                情侣游贴士：黄昏时分漫步八大关，海风、洋楼、金色阳光，是求婚拍照的热门地。
                亲子游贴士：适合骑行探索，景区内有单车租赁服务。
                """,
                "qingdao", "attraction", "青岛旅游官网-八大关", "spring,autumn"));

        docs.add(documentLoaderHelper.createFromText("""
                【崂山风景区】
                崂山是中国著名的道教名山，位于青岛市崂山区，素有"海上名山第一"之称。
                崂山主峰巨峰（又称崂顶）海拔1132.7米，是中国东部海岸线上最高的山峰。

                主要景区：
                1. 太清宫景区：道教发源地，古树参天，太清宫建于汉代，是崂山最大的道观
                2. 北九水景区：溪谷幽深，适合夏季消暑，有"九水明漪"胜景
                3. 仰口景区：海上观山，有天然浴场
                4. 棋盘石景区：观日出绝佳点

                实用信息：
                - 综合票价：旺季（5-10月）165元；淡季130元
                - 开放时间：7:00-17:00
                - 交通：青岛市区乘坐旅游专线巴士约1小时
                - 最佳季节：春季赏樱、夏季避暑、秋季赏枫、冬季观雾凇（四季皆宜）

                崂山矿泉水：景区内有天然矿泉水涌出，游客可免费饮用。
                崂山茶：产自崂山的绿茶，是馈赠亲友的特色伴手礼，"南方茶树，北方种植"。
                老人游贴士：建议选择太清宫景区，地形较为平缓，有景区观光车。
                """,
                "qingdao", "attraction", "青岛旅游官网-崂山", "all"));

        // ---- 美食类 ----
        docs.add(documentLoaderHelper.createFromText("""
                【青岛海鲜美食指南】
                青岛地处黄海之滨，海鲜是最不可错过的美食体验。

                必吃海鲜：
                1. 蛤蜊（嘎拉）：青岛最具代表性的海鲜，"吃蛤蜊喝啤酒"是青岛生活方式的象征
                   - 推荐：辣炒、清蒸，蘸酱油姜汁
                   - 价格：约15-25元/斤
                2. 皮皮虾（虾爬子）：春季最肥美，膏满黄厚
                   - 价格：约40-80元/斤（随季节浮动）
                3. 海胆：崂山海域特产，生吃最鲜
                   - 推荐餐厅：积米崖渔村、即墨鳌山渔港
                4. 墨鱼（乌贼）：青岛近海盛产，推荐清炒或做成墨鱼汁面
                5. 贝类拼盘：扇贝、花甲、海虹等

                推荐海鲜市场：
                - 团岛海鲜市场：新鲜度高，品种全，可购买后找周边餐厅代加工（加工费约10-30元/道）
                - 李村大集（周二、周五）：本地人购买海鲜的首选，价格实惠

                推荐餐厅：
                - 劈柴院（江宁路）：百年历史的小吃街，海鲜锅贴、油墨鱼、鲜炒蛤蜊
                - 登州路啤酒街：海鲜配青岛原浆啤酒，热闹氛围

                避坑提醒：景点周边海鲜餐厅价格偏高，建议在本地居民聚集区或菜市场附近就餐。
                """,
                "qingdao", "food", "青岛美食攻略-海鲜", "all"));

        docs.add(documentLoaderHelper.createFromText("""
                【青岛啤酒文化与啤酒博物馆】
                青岛啤酒始创于1903年，是中国历史最悠久的啤酒品牌之一，享誉全球。

                青岛啤酒博物馆：
                - 地址：青岛市市北区登州路56号（原青岛啤酒厂旧址）
                - 门票：约60元（含一杯原浆啤酒品鉴）
                - 开放时间：8:30-17:30
                - 亮点：
                  * 百年酿造历史展览
                  * 亲自体验灌装生产线
                  * 品鉴原浆啤酒（现场酿造，24小时内售完）
                  * 啤酒文化纪念品商店

                必喝啤酒类型：
                - 原浆啤酒：未经过滤杀菌，保留最原始麦香，只在青岛本地供应
                - 散啤：用塑料袋打包带走，是老青岛的经典记忆（约3-5元/袋）
                - 黑啤：焦香浓郁，冬季首选

                【青岛国际啤酒节】
                - 时间：每年8月第三个周末开幕，持续16天
                - 地址：青岛西海岸新区（原黄岛）啤酒城
                - 特色：全球各类精酿啤酒汇聚，现场音乐表演，烧烤海鲜美食
                - 建议提前购买预售票（约150-300元，含啤酒券）
                - 交通：地铁13号线直达
                """,
                "qingdao", "festival", "青岛旅游官网-啤酒节", "summer"));

        // ---- 交通类 ----
        docs.add(documentLoaderHelper.createFromText("""
                【青岛市内交通指南】

                公共交通：
                1. 地铁：青岛地铁网络覆盖主城区及部分郊区
                   - 1号线：贯穿南北，连接老城区与西海岸
                   - 2号线：东西走向，连接李沧、市北、市南
                   - 3号线：青岛站 → 青岛北站 → 青岛机场方向
                   - 票价：起步3元，按里程计费
                2. 公交：网络密集，覆盖景区
                   - 景区专线：栈桥、崂山等热门景点均有专线
                   - 票价：2元起步，部分线路扫码乘车优惠
                3. 出租车/网约车：
                   - 起步价：10元（3公里内）
                   - 推荐：滴滴、曹操出行在青岛运营良好

                到达青岛：
                - 飞机：青岛胶东国际机场（2021年启用），地铁8号线直达市区（约50分钟）
                - 高铁：青岛站（老城区，市南）/ 青岛北站（李沧区），北京约5.5小时，上海约5小时
                - 轮船：青岛至烟台、大连等城市有客运航线

                景区间交通建议：
                - 栈桥→八大关：步行约20分钟或公交5分钟
                - 市区→崂山太清宫：旅游专线巴士约1小时（建议早出发避开拥堵）
                - 租车自驾：适合有孩子或老人的家庭，崂山景区停车方便
                """,
                "qingdao", "transport", "青岛交通攻略", "all"));

        // ---- 住宿类 ----
        docs.add(documentLoaderHelper.createFromText("""
                【青岛住宿选择指南】

                市南区（老城区/海景）：
                - 特点：最佳地理位置，靠近栈桥、八大关、第一海水浴场
                - 适合：情侣、摄影爱好者、初次来青游客
                - 价格：中端酒店 300-600元/晚，精品民宿 200-800元/晚
                - 推荐区域：中山路周边、八大关附近、第一海水浴场沿线

                市北区（四方/台东）：
                - 特点：交通便利，商业繁华，距老城区近
                - 适合：商务出行、预算有限的背包客
                - 价格：经济连锁酒店 150-300元/晚

                崂山区：
                - 特点：依山傍海，空气清新，适合自驾游客
                - 推荐：崂山景区附近的民宿，可观看日出

                西海岸新区（黄岛）：
                - 特点：新城区，配套设施齐全，靠近万达茂、凤凰岛
                - 适合：带孩子的家庭游，有大型室内游乐设施

                订房建议：
                - 旺季（7-8月）必须提前1个月以上预订，尤其是啤酒节期间价格翻倍
                - 五一、十一黄金周提前2-3周预订
                - 性价比最高：选择台东或李沧区酒店，地铁15分钟达老城区
                """,
                "qingdao", "accommodation", "青岛住宿攻略", "all"));

        // ---- 行程规划类 ----
        docs.add(documentLoaderHelper.createFromText("""
                【青岛3天亲子游行程推荐】

                第一天：老城区+海边探索
                - 上午：栈桥（喂海鸥，拍照）→ 步行至中山路商业街（早餐：油墩、锅贴）
                - 中午：劈柴院小吃午餐（蛤蜊、海鲜小吃）
                - 下午：第一海水浴场（沙滩戏水）→ 八大关景区骑行
                - 晚餐：登州路啤酒街海鲜烧烤
                - 住宿：市南区（步行距离景点）

                第二天：崂山自然探索
                - 早餐：酒店用餐后出发
                - 上午：崂山北九水景区（溪流戏水，适合6岁以上儿童）
                - 中午：景区内农家乐（崂山豆腐、野菜饺子、崂山矿泉水）
                - 下午：崂山太清宫（了解道教文化，购买崂山茶伴手礼）
                - 晚餐：返回市区，推荐团岛海鲜市场买食材代加工

                第三天：亲子娱乐+购物
                - 上午：青岛海底世界（市南区，海洋生物科普）或极地海洋世界
                - 中午：麦岛渔港海鲜午餐
                - 下午：台东步行街购物（青岛特产：崂山啤酒、海鲜干货、崂山绿茶）
                - 傍晚：返程前在栈桥看夕阳

                亲子游注意事项：
                - 夏季防晒必备，海边紫外线强
                - 带儿童退烧药备用（海鲜易引起过敏或肠胃不适）
                - 崂山景区有电瓶车，不适合长时间步行的小朋友可乘坐
                """,
                "qingdao", "attraction", "青岛亲子游攻略", "summer"));

        docs.add(documentLoaderHelper.createFromText("""
                【青岛3天情侣/摄影游行程推荐】

                第一天：浪漫老城区
                - 黄昏：抵达青岛，傍晚栈桥看日落（黄金拍摄时间：日落前30分钟）
                - 晚餐：八大关附近精品餐厅（推荐：海景西餐）
                - 夜游：信号山公园俯瞰青岛夜景，红瓦绿树、碧海蓝天在夜晚别有风情

                第二天：文艺气息一天
                - 清晨：第一海水浴场日出拍摄（推荐角度：浴场西侧礁石区）
                - 上午：八大关景区漫步（公主楼、花石楼打卡）
                - 下午：青岛啤酒博物馆参观，品尝现酿原浆
                - 傍晚：小鱼山公园（俯瞰汇泉湾全景，情侣必去）
                - 晚餐：劈柴院老店品尝海鲜锅贴

                第三天：崂山山海
                - 早起：崂山棋盘石景区看日出（需早5点出发）
                - 上午：崂山仰口景区（海边悬崖，构图感极强）
                - 下午：太清宫道观氛围拍摄
                - 晚餐：即墨古城（距市区约1小时，夜景美丽）

                摄影装备建议：
                - 必备：滤镜（减光镜 ND64/ND1000，拍海景长曝光）
                - 三脚架：崂山山顶风大，固定必备
                - 无人机：需提前查询禁飞区（机场周边禁止）
                """,
                "qingdao", "attraction", "青岛情侣摄影游攻略", "spring,autumn"));

        return docs;
    }

    /**
     * 初始化青岛景点到 MySQL attraction 表
     */
    @Transactional
    protected void initQingdaoAttractions() {
        if (attractionRepository.findByCityCode("qingdao").isEmpty()) {
            List<Attraction> attractions = List.of(
                    Attraction.builder()
                            .cityCode("qingdao").name("栈桥").category("attraction")
                            .description("青岛标志性景点，440米海上长桥，南端建有回澜阁")
                            .address("青岛市市南区太平路22号")
                            .ticketPrice(BigDecimal.ZERO)
                            .openingHours("全天开放（回澜阁 8:30-17:00）")
                            .seasons("all").rating(new BigDecimal("4.7")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("八大关景区").category("attraction")
                            .description("万国建筑博览会，浪漫街区，四季美景各异")
                            .address("青岛市市南区山海关路1号")
                            .ticketPrice(BigDecimal.ZERO)
                            .openingHours("全天开放")
                            .seasons("spring,autumn").rating(new BigDecimal("4.8")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("崂山风景区").category("attraction")
                            .description("海上名山第一，道教圣地，主峰海拔1132米")
                            .address("青岛市崂山区")
                            .ticketPrice(new BigDecimal("165"))
                            .openingHours("7:00-17:00")
                            .seasons("all").rating(new BigDecimal("4.9")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("青岛啤酒博物馆").category("attraction")
                            .description("百年啤酒文化，原浆品鉴，工业遗址改造")
                            .address("青岛市市北区登州路56号")
                            .ticketPrice(new BigDecimal("60"))
                            .openingHours("8:30-17:30")
                            .seasons("all").rating(new BigDecimal("4.5")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("劈柴院").category("food")
                            .description("百年历史小吃街，海鲜锅贴、蛤蜊、散啤聚集地")
                            .address("青岛市市南区江宁路")
                            .ticketPrice(BigDecimal.ZERO)
                            .openingHours("9:00-22:00")
                            .seasons("all").rating(new BigDecimal("4.3")).recommended(true).build()
            );
            attractionRepository.saveAll(attractions);
            log.info("青岛景点数据初始化完成，共 {} 条", attractions.size());
        }
    }
}
