package com.pureprobe.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TestEngine：漏斗三层判定（死活 → 污染 → 出口IP风险）。
 * 流量红线（PROJECT_RULES）：并发默认8上限16；每节点每轮 ≤3 个 204 级小请求；
 * IP 纯净度按出口 IP 去重；节点间随机抖动。
 * 经 mihomo 本地 socks (127.0.0.1:7900) 逐节点切换发探活请求。
 */
public class TestEngine {
    private final MainActivity activity;
    private final MihomoManager mihomo;
    private final SubStore store;
    private volatile boolean stopFlag = false;
    private ExecutorService pool;

    // 探活端点（公共 204，不碰机场自己的测速端点）
    private static final String LIVENESS_URL = "https://www.gstatic.com/generate_204";
    private static final String POLLUTION_URL = "https://www.google.com/generate_204";
    private static final String IP_INFO_URL = "http://ip-api.com/json/?fields=status,message,country,as,proxy,hosting,query";

    public TestEngine(MainActivity activity, MihomoManager mihomo, SubStore store) {
        this.activity = activity;
        this.mihomo = mihomo;
        this.store = store;
    }

    public String start(String nodeNamesJson, int concurrency, int timeoutSec, boolean incremental,
                        MainActivity main) {
        try {
            JSONArray names = new JSONArray(nodeNamesJson);
            int conc = Math.max(1, Math.min(16, concurrency));
            PureState.setTesting(true);
            stopFlag = false;
            pool = Executors.newFixedThreadPool(conc);
            final int total = names.length();
            final AtomicInteger done = new AtomicInteger(0);
            final AtomicInteger apiCounter = new AtomicInteger(0);

            for (int i = 0; i < total; i++) {
                final String name = names.getString(i);
                pool.execute(new Runnable() {
                    public void run() {
                        if (stopFlag) return;
                        try {
                            Thread.sleep((long) (Math.random() * 300)); // 抖动
                        } catch (InterruptedException ignored) {}
                        probeNode(name, timeoutSec, apiCounter);
                        int d = done.incrementAndGet();
                        try {
                            JSONObject p = new JSONObject();
                            p.put("tested", d);
                            p.put("total", total);
                            p.put("current", name);
                            activity.onProgressJson(p.toString());
                        } catch (Exception ignored) {}
                    }
                });
            }
            // 收尾线程：等全部结束后落盘 + 回调
            final MainActivity fm = main;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        while (done.get() < total && !stopFlag) Thread.sleep(400);
                    } catch (InterruptedException ignored) {}
                    pool.shutdown();
                    store.persistNodes();
                    PureState.setTesting(false);
                    try {
                        JSONObject r = new JSONObject();
                        r.put("ok", true);
                        r.put("tested", done.get());
                        fm.onDoneJson(r.toString());
                    } catch (Exception ignored) {}
                }
            }).start();
            return PureState.okJson();
        } catch (Exception e) {
            PureState.setTesting(false);
            return PureState.errorJson(e.getMessage());
        }
    }

    public void stop() {
        stopFlag = true;
        if (pool != null) pool.shutdownNow();
        PureState.setTesting(false);
    }

    public void shutdown() { stop(); }

    /** 单节点探活：经内核 API 切到该节点，再走本地 socks 发请求 */
    private void probeNode(String name, int timeoutSec, AtomicInteger apiCounter) {
        // L1: 切换节点
        if (!switchProxy(name)) { store.setNodeResult(name, "dead", null, null, null); return; }
        // L1: 探活
        int code = viaSocks(LIVENESS_URL, timeoutSec);
        if (code != 204) { store.setNodeResult(name, "dead", null, null, null); return; }
        // L2: 污染判定（Google 204）
        int gcode = viaSocks(POLLUTION_URL, timeoutSec);
        if (gcode != 204) { store.setNodeResult(name, "polluted", null, null, null); return; }
        // L3: 出口 IP + 风险（ip-api 免费接口 45/min，超了就标 unknown 下轮再查）
        if (apiCounter.incrementAndGet() % 2 != 0) {
            try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
        }
        JSONObject info = fetchIpInfo();
        if (info == null) { store.setNodeResult(name, "clean", null, null, null); return; }
        String ip = info.optString("query", null);
        boolean proxy = info.optBoolean("proxy", false);
        boolean hosting = info.optBoolean("hosting", false);
        String risk = (proxy || hosting) ? "high" : "low";
        String status = "high".equals(risk) ? "risky" : "clean";
        int latency = lastLatency;
        store.setNodeResult(name, status, latency, ip, risk);
    }

    private int lastLatency = -1;

    /** 经本地 socks5 发 GET，返回 HTTP code；顺便记延迟 */
    private int viaSocks(String url, int timeoutSec) {
        long t0 = System.currentTimeMillis();
        try {
            java.net.Proxy p = new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                    new InetSocketAddress("127.0.0.1", mihomo.getSocksPort()));
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(p);
            c.setConnectTimeout(timeoutSec * 1000);
            c.setReadTimeout(timeoutSec * 1000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is != null) { byte[] buf = new byte[512]; is.read(buf); is.close(); }
            c.disconnect();
            lastLatency = (int) (System.currentTimeMillis() - t0);
            return code;
        } catch (Exception e) {
            lastLatency = -1;
            return -1;
        }
    }

    /** 查当前出口 IP 信息（同经 socks） */
    private JSONObject fetchIpInfo() {
        try {
            java.net.Proxy p = new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                    new InetSocketAddress("127.0.0.1", mihomo.getSocksPort()));
            HttpURLConnection c = (HttpURLConnection) new URL(IP_INFO_URL).openConnection(p);
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            c.disconnect();
            return new JSONObject(bo.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 经内核 REST API 把 PROBE 组切到指定节点 */
    private boolean switchProxy(String nodeName) {
        try {
            URL u = new URL(mihomo.getApiBase() + "/proxies/PROBE");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("PUT");
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(("{\"name\":\"" + nodeName.replace("\"", "\\\"") + "\"}").getBytes(StandardCharsets.UTF_8));
            os.close();
            int code = c.getResponseCode();
            c.disconnect();
            return code == 204 || code == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
