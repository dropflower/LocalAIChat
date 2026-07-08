package com.aiapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSearchService 单元测试类
 *
 * <p>功能描述：测试联网搜索服务的 HTML 解析、关键词提取、天气意图识别、
 * 天气数据解析和结果格式化功能。</p>
 */
class WebSearchServiceTest {

    private WebSearchService webSearchService;

    @BeforeEach
    void setUp() {
        webSearchService = new WebSearchService(new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(webSearchService, "defaultCity", "北京");
    }

    // ==================== HTML 解析测试 ====================

    @Test
    void parseSearchResults_ValidBingHtml_ShouldExtractResults() throws Exception {
        String html = "<li class=\"b_algo\">" +
                "<div class=\"b_tpcn\"><a class=\"tilk\" href=\"https://example.com/icon1\">" +
                "<div class=\"tptt\">example.com</div></a></div>" +
                "<h2><a href=\"https://example.com/page1\">Example Title</a></h2>" +
                "<div class=\"b_caption\"><p>This is a snippet.</p></div>" +
                "</li>" +
                "<li class=\"b_algo\">" +
                "<h2><a href=\"https://example.com/page2\">Another Title</a></h2>" +
                "<div class=\"b_caption\"><p>Another snippet here.</p></div>" +
                "</li>";

        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(html, "test");

        assertEquals(2, results.size());
        assertEquals("Example Title", results.get(0).title);
        assertEquals("This is a snippet.", results.get(0).snippet);
    }

    @Test
    void parseSearchResults_WithTpcnLink_ShouldUseH2TitleLink() throws Exception {
        String html = "<li class=\"b_algo\">" +
                "<div class=\"b_tpcn\"><a class=\"tilk\" href=\"https://wrong-url.com\">" +
                "<div class=\"tptt\">baidu.com</div></a></div>" +
                "<h2><a href=\"https://baike.baidu.com/item/quantum\">量子计算_百度百科</a></h2>" +
                "<div class=\"b_caption\"><p>量子计算是一种新型计算模式。</p></div>" +
                "</li>";

        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(html, "量子计算");
        assertEquals(1, results.size());
        assertEquals("量子计算_百度百科", results.get(0).title);
        assertEquals("https://baike.baidu.com/item/quantum", results.get(0).url);
    }

    @Test
    void parseSearchResults_NoResults_ShouldReturnEmptyList() throws Exception {
        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(
                "<html><body>No results</body></html>", "nonexistent");
        assertTrue(results.isEmpty());
    }

    @Test
    void parseSearchResults_HtmlEntities_ShouldBeDecoded() throws Exception {
        String html = "<li class=\"b_algo\">" +
                "<h2><a href=\"https://example.com/page\">A &amp; B</a></h2>" +
                "<div class=\"b_caption\"><p>X&#0183;Y</p></div>" +
                "</li>";

        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(html, "test");
        assertEquals("A & B", results.get(0).title);
        assertTrue(results.get(0).snippet.contains("·"));
    }

    @Test
    void parseSearchResults_MoreThanMaxResults_ShouldTruncate() throws Exception {
        StringBuilder html = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            html.append("<li class=\"b_algo\">")
                    .append("<h2><a href=\"https://example.com/page").append(i).append("\">Result ").append(i).append("</a></h2>")
                    .append("<div class=\"b_caption\"><p>Snippet ").append(i).append("</p></div>")
                    .append("</li>");
        }
        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(html.toString(), "test");
        assertEquals(5, results.size());
    }

    // ==================== 关键词提取测试 ====================

    @Test
    void extractKeywords_ChineseQuestion_ShouldRemoveStopWords() {
        assertEquals("量子计算", webSearchService.extractKeywords("什么是量子计算？"));
    }

    @Test
    void extractKeywords_ShortQuery_ShouldPreserve() {
        assertEquals("量子计算", webSearchService.extractKeywords("量子计算"));
    }

    @Test
    void extractKeywords_OnlyStopWords_ShouldFallback() {
        assertEquals("的吗了", webSearchService.extractKeywords("的吗了"));
    }

    // ==================== 天气意图识别测试 ====================

    @Test
    void isWeatherQuery_WeatherKeywords_ShouldReturnTrue() {
        assertTrue(webSearchService.isWeatherQuery("今天天气怎么样"));
        assertTrue(webSearchService.isWeatherQuery("明天会下雨吗"));
        assertTrue(webSearchService.isWeatherQuery("当前气温是多少"));
        assertTrue(webSearchService.isWeatherQuery("外面热不热"));
        assertTrue(webSearchService.isWeatherQuery("PM2.5是多少"));
    }

    @Test
    void isWeatherQuery_NonWeatherQuery_ShouldReturnFalse() {
        assertFalse(webSearchService.isWeatherQuery("量子计算是什么"));
        assertFalse(webSearchService.isWeatherQuery("Python怎么学"));
        assertFalse(webSearchService.isWeatherQuery("今天吃什么"));
    }

    @Test
    void extractCity_UserSpecifiedCity_ShouldReturnThatCity() {
        assertEquals("上海", webSearchService.extractCity("上海明天天气怎么样"));
        assertEquals("衡阳", webSearchService.extractCity("衡阳今天热不热"));
    }

    @Test
    void extractCity_NoCitySpecified_ShouldReturnDefaultCity() {
        assertEquals("北京", webSearchService.extractCity("今天天气怎么样"));
    }

    @Test
    void buildWeatherSearchQuery_ShouldCombineCityTimeAndForecast() {
        assertEquals("北京 今天 天气预报", webSearchService.buildWeatherSearchQuery("今天天气怎么样", "北京"));
        assertEquals("上海 明天 天气预报", webSearchService.buildWeatherSearchQuery("上海明天会下雨吗", "上海"));
    }

    // ==================== 天气 JSON 解析测试 ====================

    /**
     * 测试：wttr.in JSON 应正确解析为结构化天气数据
     *
     * <p>输入参数：模拟 wttr.in API 的 JSON 响应</p>
     * <p>预期结果：解析出当前气温、体感温度、天气状况、湿度等字段</p>
     */
    @Test
    void parseWeatherJson_ValidResponse_ShouldExtractWeatherData() {
        String json = """
                {
                  "current_condition": [{
                    "temp_C": "29",
                    "FeelsLikeC": "34",
                    "humidity": "75",
                    "windspeedKmph": "13",
                    "winddir16Point": "S",
                    "precipMM": "0.0",
                    "visibility": "10",
                    "uvIndex": "5",
                    "cloudcover": "68",
                    "weatherDesc": [{"value": "Cloudy"}]
                  }],
                  "weather": [{
                    "date": "2026-07-08",
                    "maxtempC": "30",
                    "mintempC": "24",
                    "hourly": [
                      {"chanceofrain": "26"},
                      {"chanceofrain": "15"},
                      {"chanceofrain": "22"}
                    ]
                  }]
                }
                """;

        String result = webSearchService.parseWeatherJson(json, "衡阳");

        assertNotNull(result);
        assertTrue(result.contains("衡阳"), "应包含城市名");
        assertTrue(result.contains("29°C"), "应包含当前气温");
        assertTrue(result.contains("34°C"), "应包含体感温度");
        assertTrue(result.contains("30°C"), "应包含最高温度");
        assertTrue(result.contains("24°C"), "应包含最低温度");
        assertTrue(result.contains("阴"), "天气状况应翻译为中文");
        assertTrue(result.contains("75%"), "应包含湿度");
        assertTrue(result.contains("南风"), "风向应翻译为中文");
        assertTrue(result.contains("13km/h"), "应包含风速");
        assertTrue(result.contains("26%"), "应包含降水概率");
    }

    /**
     * 测试：天气描述应翻译为中文
     */
    @Test
    void parseWeatherJson_EnglishDesc_ShouldTranslateToChinese() {
        String json = """
                {
                  "current_condition": [{
                    "temp_C": "31",
                    "FeelsLikeC": "36",
                    "humidity": "59",
                    "windspeedKmph": "6",
                    "winddir16Point": "S",
                    "precipMM": "0.0",
                    "visibility": "10",
                    "uvIndex": "7",
                    "cloudcover": "50",
                    "weatherDesc": [{"value": "Partly cloudy"}]
                  }],
                  "weather": [{"date": "2026-07-08", "maxtempC": "36", "mintempC": "23", "hourly": []}]
                }
                """;

        String result = webSearchService.parseWeatherJson(json, "北京");
        assertTrue(result.contains("多云"), "Partly cloudy 应翻译为多云");
        assertTrue(result.contains("南风"), "S 应翻译为南风");
    }

    /**
     * 测试：无效 JSON 应返回 null
     */
    @Test
    void parseWeatherJson_InvalidJson_ShouldReturnNull() {
        assertNull(webSearchService.parseWeatherJson("not json", "北京"));
    }

    /**
     * 测试：空 current_condition 应返回 null
     */
    @Test
    void parseWeatherJson_EmptyCurrentCondition_ShouldReturnNull() {
        String json = """
                {"current_condition": [], "weather": []}
                """;
        assertNull(webSearchService.parseWeatherJson(json, "北京"));
    }

    // ==================== 搜索上下文测试 ====================

    @Test
    void searchWithContext_WeatherQuery_ShouldSetWeatherFlag() {
        WebSearchService.SearchContext ctx = webSearchService.searchWithContext("今天天气怎么样");
        assertTrue(ctx.isWeatherQuery);
        assertEquals("北京", ctx.city);
    }

    @Test
    void searchWithContext_NonWeatherQuery_ShouldNotSetWeatherFlag() {
        WebSearchService.SearchContext ctx = webSearchService.searchWithContext("量子计算是什么");
        assertFalse(ctx.isWeatherQuery);
        assertNull(ctx.city);
        assertNull(ctx.weatherData);
    }

    // ==================== 格式化输出测试 ====================

    @Test
    void formatResults_WeatherQuery_ShouldIncludeWeatherDataAndPrompt() {
        List<WebSearchService.SearchResultItem> results = List.of(
                new WebSearchService.SearchResultItem("北京天气预报", "https://weather.com/bj", "晴 25°C")
        );
        String weatherData = "【实时天气数据 — 北京】\n当前气温: 29°C\n天气状况: 多云\n";

        String formatted = webSearchService.formatResults(results, true, "北京", weatherData);

        assertTrue(formatted.contains("北京天气查询结果"));
        assertTrue(formatted.contains("实时天气数据"));
        assertTrue(formatted.contains("29°C"));
        assertTrue(formatted.contains("回答要求"));
        assertTrue(formatted.contains("具体数字"));
    }

    @Test
    void formatResults_WeatherQueryWithOnlyWeatherData_ShouldFormatCorrectly() {
        String weatherData = "【实时天气数据 — 上海】\n当前气温: 35°C\n天气状况: 晴\n";

        String formatted = webSearchService.formatResults(List.of(), true, "上海", weatherData);

        assertTrue(formatted.contains("上海天气查询结果"));
        assertTrue(formatted.contains("35°C"));
        assertTrue(formatted.contains("回答要求"));
    }

    @Test
    void formatResults_NonWeatherQuery_ShouldUseGenericPrompt() {
        List<WebSearchService.SearchResultItem> results = List.of(
                new WebSearchService.SearchResultItem("Test", "https://example.com", "Desc")
        );
        String formatted = webSearchService.formatResults(results, false, null, null);
        assertTrue(formatted.contains("联网搜索结果"));
        assertFalse(formatted.contains("回答要求"));
    }

    @Test
    void formatResults_EmptyAll_ShouldReturnEmpty() {
        assertEquals("", webSearchService.formatResults(List.of(), false, null, null));
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private List<WebSearchService.SearchResultItem> invokeParseSearchResults(String html, String query) throws Exception {
        return (List<WebSearchService.SearchResultItem>) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                webSearchService, "parseSearchResults", html, query);
    }
}
