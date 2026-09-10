package com.pureprobe.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MihomoApiClient：内核 REST API 客户端。单一职责：HTTP 调内核 127.0.0.1:7901。
 * 无业务判断、无存储。
 */
public class MihomoApiClient {
    private final MihomoManager mgr;

    public MihomoApiClient(MihomoManager mgr) {
        this.mgr = mgr;
    }

    /** API 探活 */
    public boolean ping() {
        try {
            HttpURLConnection c = open("/version", 800);
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读 provider 节点名清单 */
    public JSONArray providerNames(String provider) throws Exception {
        HttpURLConnection c = open("/providers/proxies/" + provider, 4000);
        InputStream is = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        c.disconnect();
        JSONObject resp = new JSONObject(bo.toString("UTF-8"));
        JSONArray arr = resp.optJSONArray("proxies");
        JSONArray names = new JSONArray();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getJSONObject(i).optString("name");
                if (name.length() > 0) names.put(name);
            }
        }
        return names;
    }

    /** 把 select 组切到指定节点 */
    public boolean select(String group, String nodeName) {
        try {
            HttpURLConnection c = open("/proxies/" + group, 1500);
            c.setRequestMethod("PUT");
            c.setDoOutput(true);
            c.getOutputStream().write(
                    ("{\"name\":\"" + nodeName.replace("\"", "\\\"") + "\"}").getBytes("UTF-8"));
            c.getOutputStream().close();
            int code = c.getResponseCode();
            c.disconnect();
            return code == 204 || code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpURLConnection open(String path, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(mgr.getApiBase() + path).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        return c;
    }

    /**
     * v0.3.0 根因修复（并发竞争）：GET /group/{group}/delay
     * 内核一次性并发测全组节点，天然并发安全——不再逐节点 select+探活。
     * 旧路径根因：select 是全局状态，并发 8 时"切到 B 的同时 A 的请求还在飞"，
     * 所有并发请求测的是最后选中节点或竞争态 → 真机 72/72 全判死。
     * 本机实测（v1.19.30）：全组 72 节点一次调用 5.1s 返回 27 个延迟值（其余 5s 内无响应）。
     * 返回 {节点名: 延迟ms}；不在返回里的节点 = 5s 内无响应 = 判死。
     */
    public JSONObject groupDelay(String group, int timeoutMs) {
        try {
            String path = "/group/" + java.net.URLEncoder.encode(group, "UTF-8")
                    + "/delay?timeout=" + timeoutMs
                    + "&url=" + java.net.URLEncoder.encode("https://www.gstatic.com/generate_204", "UTF-8");
            HttpURLConnection c = open(path, timeoutMs + 5000);
            int code = c.getResponseCode();
            if (code != 200) { c.disconnect(); return null; }
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            c.disconnect();
            return new JSONObject(bo.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }
}
