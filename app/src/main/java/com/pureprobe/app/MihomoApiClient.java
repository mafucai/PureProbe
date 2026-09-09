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
}
