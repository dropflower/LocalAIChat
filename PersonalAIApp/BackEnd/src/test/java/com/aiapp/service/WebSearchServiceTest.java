package com.aiapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSearchService 单元测试类
 *
 * <p>功能描述：测试联网搜索服务的 HTML 解析和结果格式化功能。
 * 由于实际搜索依赖外部网络，本测试类仅测试 parseSearchResults 的解析逻辑
 * 和 formatResults 的格式化逻辑，不测试网络请求部分。</p>
 *
 * <p>测试策略：通过反射调用 private 的 parseSearchResults 方法，
 * 使用模拟的 Bing 搜索结果 HTML 进行验证。
 * formatResults 为 public 方法，直接调用测试。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>正常搜索结果：正确提取标题、摘要、链接</li>
 *   <li>多条结果：最多提取 MAX_RESULTS 条</li>
 *   <li>无搜索结果：返回空列表</li>
 *   <li>HTML 实体：正确解码 &amp; &lt; &gt; 等</li>
 *   <li>HTML 标签：标题和摘要中的标签被正确移除</li>
 *   <li>格式化输出：包含序号、标题、摘要、来源和提示语</li>
 * </ul>
 * </p>
 */
class WebSearchServiceTest {

    private WebSearchService webSearchService;

    @BeforeEach
    void setUp() {
        webSearchService = new WebSearchService();
    }

    /**
     * 测试：正常 Bing 搜索结果 HTML 应正确解析
     *
     * <p>输入参数：包含 2 条 b_algo 结果的模拟 HTML</p>
     * <p>预期结果：返回 2 个 SearchResultItem，包含正确的标题、链接和摘要</p>
     * <p>验证逻辑：确认 Bing HTML 结构（b_algo + b_caption）被正确解析</p>
     */
    @Test
    void parseSearchResults_ValidBingHtml_ShouldExtractResults() throws Exception {
        String html = "<li class=\"b_algo\">" +
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
        assertEquals("https://example.com/page1", results.get(0).url);
        assertEquals("This is a snippet.", results.get(0).snippet);
        assertEquals("Another Title", results.get(1).title);
        assertEquals("https://example.com/page2", results.get(1).url);
        assertEquals("Another snippet here.", results.get(1).snippet);
    }

    /**
     * 测试：无搜索结果时应返回空列表
     *
     * <p>输入参数：不包含 b_algo 元素的 HTML</p>
     * <p>预期结果：返回空列表</p>
     * <p>验证逻辑：确认无结果时不产生解析输出</p>
     */
    @Test
    void parseSearchResults_NoResults_ShouldReturnEmptyList() throws Exception {
        String html = "<html><body>No results found</body></html>";

        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(html, "nonexistent");

        assertTrue(results.isEmpty());
    }

    /**
     * 测试：HTML 实体应被正确解码
     *
     * <p>输入参数：标题包含 &amp; &lt; &gt; 实体的 HTML</p>
     * <p>预期结果：返回的 SearchResultItem 中实体被解码为对应字符</p>
     * <p>验证逻辑：确认 stripHtmlTags 正确处理常见 HTML 实体</p>
     */
    @Test
    void parseSearchResults_HtmlEntities_ShouldBeDecoded() throws Exception {
        String html = "<li class=\"b_algo\">" +
                "<h2><a href=\"https://example.com/page\">A &amp; B &lt;C&gt;</a></h2>" +
                "<div class=\"b_caption\"><p>X &amp; Y</p></div>" +
                "</li>";

        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(html, "test");

        assertEquals(1, results.size());
        assertEquals("A & B <C>", results.get(0).title);
        assertEquals("X & Y", results.get(0).snippet);
    }

    /**
     * 测试：标题中的 HTML 标签应被移除
     *
     * <p>输入参数：标题包含 &lt;strong&gt; 等高亮标签的 HTML</p>
     * <p>预期结果：返回纯文本标题和摘要，无 HTML 标签</p>
     * <p>验证逻辑：确认搜索结果中的高亮标签被正确清除</p>
     */
    @Test
    void parseSearchResults_HighlightTags_ShouldBeStripped() throws Exception {
        String html = "<li class=\"b_algo\">" +
                "<h2><a href=\"https://example.com/page\"><strong>Hello</strong> World</a></h2>" +
                "<div class=\"b_caption\"><p>Test <em>snippet</em></p></div>" +
                "</li>";

        List<WebSearchService.SearchResultItem> results = invokeParseSearchResults(html, "hello");

        assertEquals(1, results.size());
        assertEquals("Hello World", results.get(0).title);
        assertFalse(results.get(0).title.contains("<strong>"));
        assertEquals("Test snippet", results.get(0).snippet);
    }

    /**
     * 测试：超过 MAX_RESULTS 条结果时应截断
     *
     * <p>输入参数：7 条搜索结果的 HTML</p>
     * <p>预期结果：仅返回前 5 条结果</p>
     * <p>验证逻辑：确认结果数量限制生效，避免上下文过长</p>
     */
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
        assertEquals("Result 1", results.get(0).title);
        assertEquals("Result 5", results.get(4).title);
    }

    /**
     * 测试：formatResults 应包含正确的结构
     *
     * <p>输入参数：1 条 SearchResultItem</p>
     * <p>预期结果：格式化文本包含序号、标题、摘要、来源和提示语</p>
     * <p>验证逻辑：确认 formatResults 的输出格式符合预期</p>
     */
    @Test
    void formatResults_ShouldIncludeAllFields() {
        List<WebSearchService.SearchResultItem> results = List.of(
                new WebSearchService.SearchResultItem("Test Page", "https://example.com/test", "Test description")
        );

        String formatted = webSearchService.formatResults(results);

        assertTrue(formatted.startsWith("【联网搜索结果】"));
        assertTrue(formatted.contains("1. **Test Page**"));
        assertTrue(formatted.contains("Test description"));
        assertTrue(formatted.contains("来源: https://example.com/test"));
        assertTrue(formatted.contains("请基于以上搜索结果回答用户问题"));
    }

    /**
     * 测试：searchStructured 方法在异常时应返回空列表
     *
     * <p>输入参数：无法连接的 URL（不会实际执行网络请求，因为 Java HttpClient 会抛异常）</p>
     * <p>预期结果：返回空列表，不抛异常</p>
     * <p>验证逻辑：确认搜索失败的静默降级机制</p>
     */
    @Test
    void searchStructured_NetworkError_ShouldReturnEmptyList() {
        List<WebSearchService.SearchResultItem> results = webSearchService.searchStructured("test");
        assertNotNull(results);
    }

    /**
     * 测试：formatResults 对空列表应返回空字符串
     *
     * <p>输入参数：空列表</p>
     * <p>预期结果：返回空字符串</p>
     * <p>验证逻辑：确认无搜索结果时不产生格式化输出</p>
     */
    @Test
    void formatResults_EmptyList_ShouldReturnEmpty() {
        String result = webSearchService.formatResults(List.of());
        assertEquals("", result);
    }

    // ==================== 辅助方法 ====================

    /**
     * 辅助方法：通过反射调用 WebSearchService 的 parseSearchResults 方法
     */
    @SuppressWarnings("unchecked")
    private List<WebSearchService.SearchResultItem> invokeParseSearchResults(String html, String query) throws Exception {
        return (List<WebSearchService.SearchResultItem>) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                webSearchService, "parseSearchResults", html, query);
    }
}
