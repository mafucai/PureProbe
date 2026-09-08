package com.pureprobe.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

/**
 * MainActivity：WebView 壳 + JS 桥。
 * 桥方法名与 assets/js/bridge-sim.js、assets/js/bridge.js 三方一一对应（PROJECT_RULES 铁律）。
 */
public class MainActivity extends Activity {
    private WebView web;
    private MihomoManager mihomo;
    private TestEngine engine;
    private SubStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new SubStore(this);
        mihomo = new MihomoManager(this);
        store.bind(mihomo); // SubStore 刷新订阅时需启动内核
        engine = new TestEngine(this, mihomo, store);
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

    /** JS 回调：进度 / 完成（在 JS 侧由 window.onPureProgress / onPureTestDone 接收） */
    private void jsEval(final String expr) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (web != null) web.evaluateJavascript(expr, null);
            }
        });
    }

    void onProgressJson(final String json) { jsEval("onPureProgress(" + json + ")"); }
    void onDoneJson(final String json) { jsEval("onPureTestDone(" + json + ")"); }

    @Override
    protected void onDestroy() {
        if (engine != null) engine.shutdown();
        if (mihomo != null) mihomo.stop();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** JS 桥：全部方法返回 JSON 字符串；与 bridge-sim.js 方法一一对应 */
    public class Bridge {
        @JavascriptInterface
        public String getSubs() {
            return store.getSubsJson();
        }

        @JavascriptInterface
        public String addSubscription(String url) {
            String urlSafe = SubStore.maskUrl(url);
            return store.addSubscription(url, engine);
        }

        @JavascriptInterface
        public String removeSubscription(String id) {
            return store.removeSubscription(id);
        }

        @JavascriptInterface
        public String refreshSubscription(String id) {
            return store.refreshSubscription(id, engine);
        }

        @JavascriptInterface
        public String getNodes() {
            return store.getNodesJson();
        }

        @JavascriptInterface
        public String startTest(String nodeNamesJson, int concurrency, int timeoutSec, boolean incremental) {
            if (PureState.isTesting()) return "{\"ok\":false,\"error\":\"已有测试在跑\"}";
            return engine.start(nodeNamesJson, concurrency, timeoutSec, incremental, MainActivity.this);
        }

        @JavascriptInterface
        public String stopTest() {
            engine.stop();
            return "{\"ok\":true}";
        }

        @JavascriptInterface
        public String clearCache() {
            return store.clearCache();
        }

        @JavascriptInterface
        public String saveSettings(String json) {
            return store.saveSettings(json);
        }

        @JavascriptInterface
        public String getSettings() {
            return store.getSettingsJson();
        }
    }
}
