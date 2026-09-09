package com.pureprobe.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

import org.json.JSONObject;

/**
 * MainActivity：WebView 壳 + JS 桥 + 模块装配（薄壳，无业务）。
 * 桥方法名与 assets/js/bridge-sim.js、assets/js/bridge.js 三方一一对应（PROJECT_RULES 铁律）。
 * 模块：KVStore(文件) / NodeRepo(节点状态) / SubscriptionRepo(订阅+刷新调度)
 *      / MihomoManager(内核进程) / MihomoApiClient(内核API) / ProbeHttpClient(socks探活) / TestEngine(漏斗)
 */
public class MainActivity extends Activity {
    private WebView web;
    private MihomoManager mihomo;
    private MihomoApiClient api;
    private ProbeHttpClient probe;
    private NodeRepo nodeRepo;
    private SubscriptionRepo subRepo;
    private KVStore kv;
    private TestEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kv = new KVStore(this);
        nodeRepo = new NodeRepo(kv);
        mihomo = new MihomoManager(this);
        api = new MihomoApiClient(mihomo);
        probe = new ProbeHttpClient(mihomo);
        subRepo = new SubscriptionRepo(kv, nodeRepo, mihomo, api);
        engine = new TestEngine(probe, api, nodeRepo);
        setupWebView();
    }

    private void setupWebView() {
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidPure");
        web.loadUrl("file:///android_asset/index.html");
    }

    /** JS 回调统一入口：json 序列化为 JS 字符串字面量（quote 转义），JS 侧 norm() parse */
    private void jsEval(final String expr) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (web != null) web.evaluateJavascript(expr, null);
            }
        });
    }

    void onProgressJson(final String json) { jsEval("onPureProgress(" + quote(json) + ")"); }
    void onDoneJson(final String json) { jsEval("onPureTestDone(" + quote(json) + ")"); }
    void onSubReadyJson(final String json) { jsEval("onPureSubReady(" + quote(json) + ")"); }

    /** JSON 字符串 → JS 字符串字面量（带引号+转义），杜绝 eval 拼接歧义 */
    private static String quote(String s) {
        if (s == null) return "\"null\"";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append('\\').append('"'); break;
                case '\\': sb.append('\\').append('\\'); break;
                case '\n': sb.append('\\').append('n'); break;
                case '\r': sb.append('\\').append('r'); break;
                case '\t': sb.append('\\').append('t'); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        if (engine != null) engine.shutdown();
        if (mihomo != null) mihomo.stop();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** JS 桥：重活（订阅/测试）异步化 + 后台线程，桥线程永不阻塞（bug#8/#9） */
    public class Bridge {
        @JavascriptInterface
        public String getSubs() {
            return subRepo.listJson();
        }

        @JavascriptInterface
        public String addSubscription(final String url) {
            new Thread(new Runnable() {
                public void run() {
                    String added = subRepo.add(url);
                    // add 返回 {ok,exists?,id?}；存在或新增都要拉节点
                    String result = added;
                    try {
                        JSONObject o = new JSONObject(added);
                        if (o.optBoolean("ok") && o.optString("id").length() > 0) {
                            result = subRepo.refresh(o.optString("id"));
                        }
                    } catch (Exception ignored) {}
                    onSubReadyJson(result);
                }
            }, "pp-addsub").start();
            return "{\"ok\":true,\"async\":true}";
        }

        @JavascriptInterface
        public String removeSubscription(String id) {
            return subRepo.remove(id);
        }

        @JavascriptInterface
        public String refreshSubscription(final String id) {
            new Thread(new Runnable() {
                public void run() {
                    onSubReadyJson(subRepo.refresh(id));
                }
            }, "pp-refsub").start();
            return "{\"ok\":true,\"async\":true}";
        }

        @JavascriptInterface
        public String getNodes() {
            return nodeRepo.toJsList().toString();
        }

        @JavascriptInterface
        public String startTest(final String nodeNamesJson, final int concurrency, final int timeoutSec, final boolean incremental) {
            new Thread(new Runnable() {
                public void run() {
                    String result = engine.start(nodeNamesJson, concurrency, timeoutSec,
                            new TestEngine.ProgressCb() {
                                public void on(int tested, int total, String current) {
                                    try {
                                        JSONObject p = new JSONObject();
                                        p.put("tested", tested);
                                        p.put("total", total);
                                        p.put("current", current);
                                        onProgressJson(p.toString());
                                    } catch (Exception ignored) {}
                                }
                            },
                            new TestEngine.DoneCb() {
                                public void on(int tested) {
                                    try {
                                        JSONObject r = new JSONObject();
                                        r.put("ok", true);
                                        r.put("tested", tested);
                                        onDoneJson(r.toString());
                                    } catch (Exception ignored) {}
                                }
                            });
                    // 启动失败（如已在测）也回调，前端复位按钮
                    try {
                        JSONObject o = new JSONObject(result);
                        if (!o.optBoolean("ok")) onDoneJson(result);
                    } catch (Exception ignored) {}
                }
            }, "pp-starttest").start();
            return "{\"ok\":true,\"async\":true}";
        }

        @JavascriptInterface
        public String stopTest() {
            engine.stop();
            return "{\"ok\":true}";
        }

        @JavascriptInterface
        public String clearCache() {
            nodeRepo.clearResults();
            nodeRepo.persist();
            return PureState.okJson();
        }

        @JavascriptInterface
        public String saveSettings(String json) {
            try {
                JSONObject in = new JSONObject(json);
                int conc = in.optInt("concurrency", 8);
                if (conc < 1) conc = 1;
                if (conc > 16) conc = 16; // 红线：并发硬上限 16
                in.put("concurrency", conc);
                kv.write("pureprobe_settings.json", in);
                return PureState.okJson();
            } catch (Exception e) {
                return PureState.errorJson("internal");
            }
        }

        @JavascriptInterface
        public String getSettings() {
            try {
                if (kv.exists("pureprobe_settings.json")) return kv.readRaw("pureprobe_settings.json");
            } catch (Exception ignored) {}
            return "{\"concurrency\":8,\"timeoutSec\":5,\"cacheHours\":24,\"incremental\":true}";
        }
    }
}
