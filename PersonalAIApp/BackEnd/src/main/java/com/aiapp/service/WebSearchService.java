package com.aiapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网搜索服务
 *
 * 使用 Bing 搜索（无需 API Key），通过解析 HTML 页面提取搜索结果。
 * 天气查询使用 wttr.in API 获取实时天气数据。
 * 搜索结果作为额外上下文注入到 AI 对话中，帮助模型提供更准确的回答。
 *
 * ## 天气查询优化
 * 1. 意图识别：检测天气相关关键词
 * 2. 城市提取：从用户消息中提取城市名，未指定时使用默认城市
 * 3. 实时数据：调用 wttr.in API 获取当前温度、天气状况、湿度、风力等
 * 4. 搜索补充：同时执行 Bing 搜索获取天气预报网站信息
 * 5. 结构化提示：将实时天气数据放在最前面，引导 AI 直接回答
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private static final String SEARCH_URL = "https://www.bing.com/search?q=";
    private static final String WEATHER_API_URL = "https://wttr.in/";
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESULTS = 5;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36";

    @Value("${app.search.default-city:北京}")
    private String defaultCity;

    private final ObjectMapper objectMapper;

    private static final Set<String> WEATHER_KEYWORDS = Set.of(
            "天气", "气温", "温度", "下雨", "下雨吗", "下雪", "下雪吗",
            "晴天", "阴天", "多云", "刮风", "风速", "湿度",
            "暴雨", "大雨", "小雨", "雷阵雨", "台风",
            "空气质量", "pm2.5", "雾霾", "沙尘",
            "紫外线", "日出", "日落", "穿衣", "洗车",
            "几度", "多少度", "冷不冷", "热不热",
            "天气预报", "气象", "降温", "升温", "最高温", "最低温"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "什么是", "什么叫", "什么是的", "如何", "怎么", "怎样",
            "为什么", "请问", "帮我", "告诉我", "解释一下", "介绍一下",
            "能不能", "可以吗", "吗", "呢", "啊", "吧", "嘛",
            "的", "了", "是", "在", "有", "和", "与", "或",
            "及", "等", "这", "那", "它", "其", "被", "把",
            "给", "让", "用", "从", "到", "对", "向", "为",
            "关于", "对于", "之间", "什么", "哪些", "哪个",
            "多少", "几", "谁", "哪", "何时", "哪里"
    );

    private static final Set<String> MAJOR_CITIES = Set.of(
            "北京", "上海", "广州", "深圳", "成都", "杭州", "武汉", "南京",
            "重庆", "天津", "苏州", "西安", "长沙", "沈阳", "青岛", "郑州",
            "大连", "东莞", "宁波", "厦门", "福州", "无锡", "合肥", "昆明",
            "哈尔滨", "济南", "佛山", "长春", "温州", "石家庄", "南宁", "贵阳",
            "南昌", "太原", "乌鲁木齐", "兰州", "拉萨", "海口", "呼和浩特",
            "衡阳", "珠海", "泉州", "唐山", "烟台", "桂林", "扬州", "洛阳"
    );

    /**
     * 城市名到英文的映射（wttr.in 使用英文城市名）
     */
    private static final Map<String, String> CITY_EN_MAP = Map.ofEntries(
            Map.entry("北京", "Beijing"), Map.entry("上海", "Shanghai"),
            Map.entry("广州", "Guangzhou"), Map.entry("深圳", "Shenzhen"),
            Map.entry("成都", "Chengdu"), Map.entry("杭州", "Hangzhou"),
            Map.entry("武汉", "Wuhan"), Map.entry("南京", "Nanjing"),
            Map.entry("重庆", "Chongqing"), Map.entry("天津", "Tianjin"),
            Map.entry("苏州", "Suzhou"), Map.entry("西安", "Xian"),
            Map.entry("长沙", "Changsha"), Map.entry("沈阳", "Shenyang"),
            Map.entry("青岛", "Qingdao"), Map.entry("郑州", "Zhengzhou"),
            Map.entry("大连", "Dalian"), Map.entry("东莞", "Dongguan"),
            Map.entry("宁波", "Ningbo"), Map.entry("厦门", "Xiamen"),
            Map.entry("福州", "Fuzhou"), Map.entry("无锡", "Wuxi"),
            Map.entry("合肥", "Hefei"), Map.entry("昆明", "Kunming"),
            Map.entry("哈尔滨", "Harbin"), Map.entry("济南", "Jinan"),
            Map.entry("佛山", "Foshan"), Map.entry("长春", "Changchun"),
            Map.entry("衡阳", "Hengyang"), Map.entry("贵阳", "Guiyang"),
            Map.entry("南宁", "Nanning"), Map.entry("拉萨", "Lhasa"),
            Map.entry("海口", "Haikou"), Map.entry("太原", "Taiyuan"),
            Map.entry("兰州", "Lanzhou"), Map.entry("乌鲁木齐", "Urumqi")
    );

    public static class SearchResultItem {
        public final String title;
        public final String url;
        public final String snippet;

        public SearchResultItem(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }

    public static class SearchContext {
        public final List<SearchResultItem> results;
        public final boolean isWeatherQuery;
        public final String city;
        public final String weatherData;

        public SearchContext(List<SearchResultItem> results, boolean isWeatherQuery,
                            String city, String weatherData) {
            this.results = results;
            this.isWeatherQuery = isWeatherQuery;
            this.city = city;
            this.weatherData = weatherData;
        }
    }

    /**
     * 执行联网搜索，返回搜索上下文
     */
    public SearchContext searchWithContext(String query) {
        boolean isWeather = isWeatherQuery(query);
        String city = isWeather ? extractCity(query) : null;
        String weatherData = null;

        if (isWeather) {
            weatherData = fetchWeatherData(city);
        }

        String searchQuery = isWeather
                ? buildWeatherSearchQuery(query, city)
                : extractKeywords(query);

        List<SearchResultItem> results = executeSearch(searchQuery);

        if (results.isEmpty() && !searchQuery.equals(query)) {
            log.warn("关键词搜索无结果，尝试使用原始查询: {}", query);
            results = executeSearch(query);
        }

        return new SearchContext(results, isWeather, city, weatherData);
    }

    public List<SearchResultItem> searchStructured(String query) {
        return searchWithContext(query).results;
    }

    // ==================== 天气数据获取 ====================

    /**
     * 从 wttr.in 获取实时天气数据
     *
     * 调用 wttr.in JSON API，提取当前温度、天气状况、湿度、风力等信息，
     * 格式化为结构化文本注入到对话上下文中。
     *
     * @param city 中文城市名
     * @return 格式化的天气数据文本，失败返回 null
     */
    String fetchWeatherData(String city) {
        try {
            String englishCity = CITY_EN_MAP.getOrDefault(city, city);
            String url = WEATHER_API_URL + URLEncoder.encode(englishCity, StandardCharsets.UTF_8) + "?format=j1";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(SEARCH_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "curl/7.68.0")
                    .timeout(SEARCH_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("天气 API 返回非 200 状态码: {}", response.statusCode());
                return null;
            }

            return parseWeatherJson(response.body(), city);
        } catch (Exception e) {
            log.warn("获取天气数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 wttr.in JSON 响应，提取关键天气信息
     */
    String parseWeatherJson(String json, String city) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode current = root.path("current_condition");
            if (current.isEmpty() || !current.isArray() || current.size() == 0) {
                return null;
            }
            JsonNode curr = current.get(0);

            String tempC = curr.path("temp_C").asText();
            String feelsLikeC = curr.path("FeelsLikeC").asText();
            String humidity = curr.path("humidity").asText();
            String windSpeed = curr.path("windspeedKmph").asText();
            String windDir = curr.path("winddir16Point").asText();
            String precipMM = curr.path("precipMM").asText();
            String visibility = curr.path("visibility").asText();
            String uvIndex = curr.path("uvIndex").asText();
            String cloudCover = curr.path("cloudcover").asText();

            String weatherDesc = curr.path("weatherDesc").get(0).path("value").asText();

            // 获取今日预报
            JsonNode weather = root.path("weather");
            String todayMax = "";
            String todayMin = "";
            String todayDate = "";
            if (!weather.isEmpty() && weather.isArray() && weather.size() > 0) {
                JsonNode today = weather.get(0);
                todayMax = today.path("maxtempC").asText();
                todayMin = today.path("mintempC").asText();
                todayDate = today.path("date").asText();
            }

            // 获取降水概率（取今日各时段最大值）
            String maxRainChance = "0";
            if (!weather.isEmpty() && weather.isArray() && weather.size() > 0) {
                JsonNode today = weather.get(0);
                JsonNode hourly = today.path("hourly");
                if (hourly.isArray()) {
                    for (JsonNode hour : hourly) {
                        int chance = hour.path("chanceofrain").asInt(0);
                        if (chance > Integer.parseInt(maxRainChance)) {
                            maxRainChance = String.valueOf(chance);
                        }
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【实时天气数据 — ").append(city).append("】\n");
            sb.append("日期: ").append(todayDate).append("\n");
            sb.append("当前气温: ").append(tempC).append("°C\n");
            sb.append("体感温度: ").append(feelsLikeC).append("°C\n");
            sb.append("今日最高/最低: ").append(todayMax).append("°C / ").append(todayMin).append("°C\n");
            sb.append("天气状况: ").append(translateWeatherDesc(weatherDesc)).append("\n");
            sb.append("云量: ").append(cloudCover).append("%\n");
            sb.append("湿度: ").append(humidity).append("%\n");
            sb.append("风向/风速: ").append(translateWindDir(windDir)).append(" ").append(windSpeed).append("km/h\n");
            sb.append("降水量: ").append(precipMM).append("mm\n");
            sb.append("降水概率: ").append(maxRainChance).append("%\n");
            sb.append("能见度: ").append(visibility).append("km\n");
            sb.append("紫外线指数: ").append(uvIndex).append("\n");

            return sb.toString();
        } catch (Exception e) {
            log.warn("解析天气 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 翻译天气描述为中文
     */
    private String translateWeatherDesc(String desc) {
        if (desc == null) return "未知";
        String[][] translations = {
                {"Sunny", "晴"}, {"Clear", "晴"}, {"Partly cloudy", "多云"},
                {"Partly Cloudy", "多云"}, {"Cloudy", "阴"}, {"Overcast", "阴天"},
                {"Mist", "薄雾"}, {"Fog", "雾"}, {"Light rain", "小雨"},
                {"Light rain shower", "小阵雨"}, {"Moderate rain", "中雨"},
                {"Heavy rain", "大雨"}, {"Patchy rain nearby", "零星小雨"},
                {"Patchy light drizzle", "零星细雨"}, {"Thunderstorm", "雷暴"},
                {"Thundery outbreaks", "雷阵雨"}, {"Light snow", "小雪"},
                {"Heavy snow", "大雪"}, {"Blizzard", "暴风雪"},
                {"Freezing fog", "冻雾"}, {"Hail", "冰雹"},
                {"Smoky haze", "雾霾"}, {"Blowing snow", "风吹雪"}
        };
        for (String[] pair : translations) {
            if (desc.toLowerCase().contains(pair[0].toLowerCase())) {
                return pair[1];
            }
        }
        return desc;
    }

    private String translateWindDir(String dir) {
        String[][] dirs = {
                {"N", "北风"}, {"NNE", "北东北风"}, {"NE", "东北风"}, {"ENE", "东东北风"},
                {"E", "东风"}, {"ESE", "东东南风"}, {"SE", "东南风"}, {"SSE", "南东南风"},
                {"S", "南风"}, {"SSW", "南西南风"}, {"SW", "西南风"}, {"WSW", "西西南风"},
                {"W", "西风"}, {"WNW", "西西北风"}, {"NW", "西北风"}, {"NNW", "北西北风"}
        };
        for (String[] pair : dirs) {
            if (pair[0].equals(dir)) return pair[1];
        }
        return dir;
    }

    // ==================== 意图识别 ====================

    boolean isWeatherQuery(String query) {
        if (query == null) return false;
        String lower = query.toLowerCase();
        for (String keyword : WEATHER_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    String extractCity(String query) {
        if (query == null) return defaultCity;
        for (String city : MAJOR_CITIES) {
            if (query.contains(city)) return city;
        }
        return defaultCity;
    }

    String buildWeatherSearchQuery(String query, String city) {
        String timeKeyword = extractTimeKeyword(query);
        return city + " " + timeKeyword + " 天气预报";
    }

    private String extractTimeKeyword(String query) {
        if (query.contains("明天")) return "明天";
        if (query.contains("后天")) return "后天";
        if (query.contains("大后天")) return "大后天";
        if (query.contains("今天") || query.contains("当前") || query.contains("现在")) return "今天";
        if (query.contains("本周") || query.contains("这周")) return "本周";
        if (query.contains("下周")) return "下周";
        if (query.contains("周末")) return "周末";
        Matcher weekDayMatcher = Pattern.compile("周([一二三四五六日天])").matcher(query);
        if (weekDayMatcher.find()) return "周" + weekDayMatcher.group(1);
        return "今天";
    }

    // ==================== 格式化 ====================

    public String formatResults(List<SearchResultItem> results) {
        return formatResults(results, false, null, null);
    }

    /**
     * 格式化搜索结果为注入到对话上下文中的文本
     *
     * 对于天气查询，实时天气数据放在最前面（AI 优先读取），
     * 搜索结果作为补充信息，系统提示词要求 AI 直接回答具体数据。
     */
    public String formatResults(List<SearchResultItem> results, boolean isWeatherQuery,
                                String city, String weatherData) {
        if (results.isEmpty() && (weatherData == null || weatherData.isEmpty())) return "";

        StringBuilder sb = new StringBuilder();

        if (isWeatherQuery) {
            sb.append("【").append(city != null ? city : "").append("天气查询结果】\n\n");

            // 实时天气数据优先展示
            if (weatherData != null && !weatherData.isEmpty()) {
                sb.append(weatherData).append("\n");
            }

            // Bing 搜索结果作为补充
            if (!results.isEmpty()) {
                sb.append("【补充天气信息来源】\n\n");
                for (int i = 0; i < results.size(); i++) {
                    SearchResultItem r = results.get(i);
                    sb.append(i + 1).append(". **").append(r.title).append("**\n");
                    if (!r.snippet.isEmpty()) {
                        sb.append("   ").append(r.snippet).append("\n");
                    }
                    sb.append("   来源: ").append(r.url).append("\n\n");
                }
            }

            sb.append("【回答要求】\n");
            sb.append("1. 必须优先使用「实时天气数据」中的具体数值直接回答\n");
            sb.append("2. 回答格式：先说当前气温和体感温度，再说天气状况，然后补充湿度/风力/降水等\n");
            sb.append("3. 不要笼统描述气候特征，必须给出具体数字（如：当前29°C、多云、湿度75%）\n");
            sb.append("4. 如果用户问是否会下雨，必须引用降水概率和降水量数据\n");
            sb.append("5. 如果用户问气温，必须引用当前气温和今日最高/最低温度\n");
        } else {
            sb.append("【联网搜索结果】\n\n");
            for (int i = 0; i < results.size(); i++) {
                SearchResultItem r = results.get(i);
                sb.append(i + 1).append(". **").append(r.title).append("**\n");
                if (!r.snippet.isEmpty()) {
                    sb.append("   ").append(r.snippet).append("\n");
                }
                sb.append("   来源: ").append(r.url).append("\n\n");
            }
            sb.append("请基于以上搜索结果回答用户问题，并在回答中引用相关来源。");
        }

        return sb.toString();
    }

    // ==================== 关键词提取 ====================

    String extractKeywords(String query) {
        if (query == null || query.trim().isEmpty()) return query;

        String cleaned = query.trim();
        cleaned = cleaned.replaceAll("[？?！!。，,、；;：\u201c\u201d\u2018\u2019\uff08\uff09()\\[\\]【】{}《》<>]", " ");

        List<String> sortedStopWords = new ArrayList<>(STOP_WORDS);
        sortedStopWords.sort((a, b) -> b.length() - a.length());
        for (String stopWord : sortedStopWords) {
            cleaned = cleaned.replace(stopWord, " ");
        }

        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        if (cleaned.isEmpty() || cleaned.length() < 2) {
            return query.trim();
        }

        if (cleaned.length() > 30) {
            cleaned = cleaned.substring(0, 30);
        }

        return cleaned;
    }

    // ==================== 搜索执行 ====================

    private List<SearchResultItem> executeSearch(String searchQuery) {
        try {
            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
            String url = SEARCH_URL + encodedQuery;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(SEARCH_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(SEARCH_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return parseSearchResults(response.body(), searchQuery);
        } catch (Exception e) {
            log.warn("联网搜索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== HTML 解析 ====================

    private List<SearchResultItem> parseSearchResults(String html, String query) {
        List<SearchResultItem> results = new ArrayList<>();

        Pattern blockPattern = Pattern.compile(
                "<li\\s+class=\"b_algo\"[^>]*>(.*?)</li>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Pattern titlePattern = Pattern.compile(
                "<h2[^>]*>\\s*<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>\\s*</h2>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Pattern snippetPattern = Pattern.compile(
                "class=\"b_caption\"[^>]*>.*?<p[^>]*>(.*?)</p>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher blockMatcher = blockPattern.matcher(html);
        while (blockMatcher.find() && results.size() < MAX_RESULTS) {
            String block = blockMatcher.group(1);

            Matcher titleMatcher = titlePattern.matcher(block);
            if (!titleMatcher.find()) continue;

            String url = titleMatcher.group(1).trim();
            String title = decodeHtmlEntities(titleMatcher.group(2)).trim();

            String snippet = "";
            Matcher snippetMatcher = snippetPattern.matcher(block);
            if (snippetMatcher.find()) {
                snippet = decodeHtmlEntities(snippetMatcher.group(1)).trim()
                        .replaceAll("\\s+", " ");
            }

            if (title.isEmpty() || url.isEmpty()) continue;

            results.add(new SearchResultItem(title, url, snippet));
        }

        if (results.isEmpty()) {
            log.warn("未解析到搜索结果，搜索词: {}", query);
        }

        return results;
    }

    private String decodeHtmlEntities(String html) {
        String text = html.replaceAll("<[^>]+>", "");

        StringBuffer sb = new StringBuffer();
        Matcher hexMatcher = Pattern.compile("&#x([0-9a-fA-F]+);").matcher(text);
        while (hexMatcher.find()) {
            try {
                int codePoint = Integer.parseInt(hexMatcher.group(1), 16);
                hexMatcher.appendReplacement(sb, new String(Character.toChars(codePoint)));
            } catch (Exception e) {
                hexMatcher.appendReplacement(sb, "");
            }
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();

        sb = new StringBuffer();
        Matcher decMatcher = Pattern.compile("&#(\\d+);").matcher(text);
        while (decMatcher.find()) {
            try {
                int codePoint = Integer.parseInt(decMatcher.group(1));
                decMatcher.appendReplacement(sb, new String(Character.toChars(codePoint)));
            } catch (Exception e) {
                decMatcher.appendReplacement(sb, "");
            }
        }
        decMatcher.appendTail(sb);
        text = sb.toString();

        text = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&ensp;", " ")
                .replace("&emsp;", " ")
                .replace("&nbsp;", " ")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…")
                .replace("&copy;", "©")
                .replace("&reg;", "®")
                .replace("&trade;", "™");

        return text;
    }
}
