package com.pureprobe.app;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/**
 * ProbeHttpClient：经 mihomo 本地 socks 的探活 HTTP 客户端。
 * 单一职责：发请求、回状态码/延迟/响应体。无判定逻辑、无存储。
 */
public class ProbeHttpClient {
    private final MihomoManager mgr;
    private int lastLatency = -1;

    public ProbeHttpClient(MihomoManager mgr) {
        this.mgr = mgr;
    }

    /** 期望 code 的探活（204 等）。通= true */
    public boolean expect(String url, int expectCode, int timeoutSec) {
        return get(url, timeoutSec) == expectCode;
    }

    /** GET 经 mihomo 混合端口，返回 HTTP code；-1 = 异常。
     *  必须走 HTTP CONNECT 而非 SOCKS：Java SOCKS 代理在本地解析 DNS（会被污染），
     *  HTTP 代理把域名交给内核远程解析——与用户正常翻墙行为一致（bug#16） */
    public int get(String url, int timeoutSec) {
        long t0 = System.currentTimeMillis();
        try {
            Proxy p = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress("127.0.0.1", mgr.getSocksPort()));
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(p);
            c.setConnectTimeout(timeoutSec * 1000);
            c.setReadTimeout(timeoutSec * 1000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is != null) {
                byte[] buf = new byte[512];
                is.read(buf);
                is.close();
            }
            c.disconnect();
            lastLatency = (int) (System.currentTimeMillis() - t0);
            return code;
        } catch (Exception e) {
            lastLatency = -1;
            return -1;
        }
    }

    /** GET 并返回 JSON 体（经 mihomo 混合端口 HTTP CONNECT，域名由内核远程解析） */
    public JSONObject getJson(String url, int timeoutSec) {
        final long t0 = System.currentTimeMillis();
        try {
            Proxy p = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress("127.0.0.1", mgr.getSocksPort()));
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(p);
            c.setConnectTimeout(timeoutSec * 1000);
            c.setReadTimeout(timeoutSec * 1000);
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            c.disconnect();
            lastLatency = (int) (System.currentTimeMillis() - t0);
            return new JSONObject(bo.toString("UTF-8"));
        } catch (Exception e) {
            lastLatency = -1;
            return null;
        }
    }

    public int lastLatency() {
        return lastLatency;
    }
}
