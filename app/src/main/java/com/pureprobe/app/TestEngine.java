package com.pureprobe.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TestEngine：漏斗三层判定调度（薄层）。
 * L1 死活(204) → L2 污染(Google 204) → L3 出口IP风险(ip-api)。
 * 请求委托 ProbeHttpClient，切节点委托 MihomoApiClient，结果写 NodeRepo。
 * 流量红线：并发≤16 硬上限；每节点每轮 ≤3 个 204 级请求；IP 查询按出口去重由缓存自然覆盖。
 */
public class TestEngine {
    private final ProbeHttpClient http;
    private final MihomoApiClient api;
    private final NodeRepo nodeRepo;
    private volatile boolean stopFlag = false;
    private ExecutorService pool;

    private static final String LIVENESS_URL = "https://www.gstatic.com/generate_204";
    private static final String POLLUTION_URL = "https://www.google.com/generate_204";
    private static final String IP_INFO_URL = "http://ip-api.com/json/?fields=status,message,country,as,proxy,hosting,query";

    public TestEngine(ProbeHttpClient http, MihomoApiClient api, NodeRepo nodeRepo) {
        this.http = http;
        this.api = api;
        this.nodeRepo = nodeRepo;
    }

    /** 启动漏斗测试。progressCb/doneCb 由 MainActivity 注入（JS 回调） */
    public String start(String nodeNamesJson, int concurrency, int timeoutSec,
                        final ProgressCb progressCb, final DoneCb doneCb) {
        try {
            if (PureState.isTesting()) return PureState.errorJson("已有测试在跑");
            JSONArray names = new JSONArray(nodeNamesJson);
            int conc = Math.max(1, Math.min(16, concurrency));
            PureState.setTesting(true);
            stopFlag = false;
            pool = Executors.newFixedThreadPool(conc);
            final int total = names.length();
            final AtomicInteger done = new AtomicInteger(0);

            for (int i = 0; i < total; i++) {
                final String name = names.getString(i);
                pool.execute(new Runnable() {
                    public void run() {
                        if (stopFlag) return;
                        try { Thread.sleep((long) (Math.random() * 300)); } catch (InterruptedException ignored) {}
                        probeNode(name, timeoutSec);
                        int d = done.incrementAndGet();
                        if (progressCb != null) progressCb.on(d, total, name);
                    }
                });
            }
            new Thread(new Runnable() {
                public void run() {
                    try {
                        while (done.get() < total && !stopFlag) Thread.sleep(400);
                    } catch (InterruptedException ignored) {}
                    pool.shutdown();
                    nodeRepo.persist();
                    PureState.setTesting(false);
                    if (doneCb != null) doneCb.on(done.get());
                }
            }, "pp-test-finish").start();
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

    /** 漏斗判定：每节点状态机 */
    private void probeNode(String name, int timeoutSec) {
        if (!api.select("PROBE", name)) {
            nodeRepo.setResult(name, "dead", null, null, null);
            return;
        }
        if (!http.expect(LIVENESS_URL, 204, timeoutSec)) {
            nodeRepo.setResult(name, "dead", null, null, null);
            return;
        }
        if (!http.expect(POLLUTION_URL, 204, timeoutSec)) {
            nodeRepo.setResult(name, "polluted", null, null, null);
            return;
        }
        // L3: 出口 IP 风险（ip-api 免费版 45/min，简单节流）
        try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
        JSONObject info = http.getJson(IP_INFO_URL, 6);
        if (info == null) {
            nodeRepo.setResult(name, "clean", http.lastLatency(), null, null);
            return;
        }
        boolean proxy = info.optBoolean("proxy", false);
        boolean hosting = info.optBoolean("hosting", false);
        String exitIp = info.optString("query", null);
        if (proxy || hosting) {
            nodeRepo.setResult(name, "risky", http.lastLatency(), exitIp, "high");
        } else {
            nodeRepo.setResult(name, "clean", http.lastLatency(), exitIp, "low");
        }
    }

    public interface ProgressCb { void on(int tested, int total, String current); }
    public interface DoneCb { void on(int tested); }
}
