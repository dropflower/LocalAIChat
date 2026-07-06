package com.aiapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 搜索结果作为额外上下文注入到 AI 对话中，帮助模型提供更准确的回答。
 *
 * 返回 SearchResult 对象列表，包含标题、摘要、链接，
 * 由 ChatService 生成 SSE 搜索事件和上下文注入文本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private static final String SEARCH_URL = "https://www.bing.com/search?q=";
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESULTS = 5;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36";

    /**
     * 搜索结果条目
     */
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

    /**
     * 执行联网搜索，返回结构化搜索结果
     *
     * @param query 搜索关键词
     * @return 搜索结果列表，如果搜索失败返回空列表
     */
    public List<SearchResultItem> searchStructured(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
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
            return parseSearchResults(response.body(), query);
        } catch (Exception e) {
            log.warn("联网搜索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 将搜索结果格式化为注入到对话上下文中的文本
     */
    public String formatResults(List<SearchResultItem> results) {
        if (results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private List<SearchResultItem> parseSearchResults(String html, String query) {
        List<SearchResultItem> results = new ArrayList<>();

        Pattern blockPattern = Pattern.compile(
                "<li\\s+class=\"b_algo\"[^>]*>(.*?)</li>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Pattern linkPattern = Pattern.compile(
                "<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Pattern snippetPattern = Pattern.compile(
                "class=\"b_caption\"[^>]*>.*?<p[^>]*>(.*?)</p>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher blockMatcher = blockPattern.matcher(html);
        while (blockMatcher.find() && results.size() < MAX_RESULTS) {
            String block = blockMatcher.group(1);

            Matcher linkMatcher = linkPattern.matcher(block);
            if (!linkMatcher.find()) continue;

            String url = linkMatcher.group(1).trim();
            String title = stripHtmlTags(linkMatcher.group(2)).trim();

            String snippet = "";
            Matcher snippetMatcher = snippetPattern.matcher(block);
            if (snippetMatcher.find()) {
                snippet = stripHtmlTags(snippetMatcher.group(1)).trim()
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

    private String stripHtmlTags(String html) {
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ");
    }
}
