package com.pureprobe.app;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * MihomoManager：mihomo 内核子进程生命周期。
 * - 内核二进制位于 jniLibs/arm64-v8a/libmihomo.so，运行时从 nativeLibraryDir 执行
 *   （Android 10+ 禁止 exec 应用数据目录文件，借鉴 ClashMetaForAndroid 做法）
 * - 只监听 127.0.0.1，无 VPN、无全局代理
 */
public class MihomoManager {
    private final Context ctx;
    private Process proc;
    private int socksPort = 7900;
    private int apiPort = 7901;
    private String apiSecret = "";

    public MihomoManager(Context ctx) {
        this.ctx = ctx;
    }

    public int getSocksPort() { return socksPort; }
    public int getApiPort() { return apiPort; }
    public String getApiBase() { return "http://127.0.0.1:" + apiPort; }

    /** 生成最小内核配置（provider 吃订阅），写入 filesDir/mihomo.yaml */
    public File writeConfig(String subUrl) throws IOException {
        File dir = new File(ctx.getFilesDir(), "mihomo");
        if (!dir.exists()) dir.mkdirs();
        File cfg = new File(dir, "config.yaml");
        String yaml = "mixed-port: " + socksPort + "\n"
                + "bind-address: 127.0.0.1\n"
                + "allow-lan: false\n"
                + "mode: global\n"
                + "log-level: warning\n"
                + "external-controller: 127.0.0.1:" + apiPort + "\n"
                + "secret: \"" + apiSecret + "\"\n"
                + "proxies: []\n"
                + "proxy-groups:\n"
                + "  - name: PROBE\n"
                + "    type: select\n"
                + "    proxies:\n"
                + "      - AUTOPOOL\n"
                + "  - name: AUTOPOOL\n"
                + "    type: url-test\n"
                + "    url: https://www.gstatic.com/generate_204\n"
                + "    interval: 600\n"
                + "    use:\n"
                + "      - sub\n"
                + "proxy-providers:\n"
                + "  sub:\n"
                + "    type: http\n"
                + "    url: \"" + subUrl + "\"\n"
                + "    interval: 86400\n"
                + "    path: ./providers/sub.yaml\n"
                + "    health-check:\n"
                + "      enable: false\n";
        FileOutputStream fo = new FileOutputStream(cfg);
        fo.write(yaml.getBytes("UTF-8"));
        fo.close();
        return cfg;
    }

    /** 启动内核。subUrl 为订阅地址（自动补 flag=meta 以便解析出全部协议） */
    public synchronized boolean start(String subUrl) throws IOException {
        if (isRunning()) return true;
        String bin = ctx.getApplicationInfo().nativeLibraryDir + "/libmihomo.so";
        File f = new File(bin);
        if (!f.exists()) {
            throw new IOException("内核二进制缺失 (nativeLibraryDir=" + ctx.getApplicationInfo().nativeLibraryDir
                    + ", 需重装触发解压)");
        }
        if (!f.canExecute() && !f.setExecutable(true, false)) {
            throw new IOException("内核无执行权限");
        }
        File cfg = writeConfig(SubStore.ensureMetaFlag(subUrl));
        ProcessBuilder pb = new ProcessBuilder(bin, "-d", cfg.getParent(), "-f", cfg.getAbsolutePath());
        pb.environment().put("HOME", ctx.getFilesDir().getAbsolutePath());
        pb.redirectErrorStream(true);
        proc = pb.start();
        // 简单健康等待：API 就绪或超时
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (probeApi()) return true;
            if (proc == null) break;
            try { Thread.sleep(300); } catch (InterruptedException ie) { break; }
        }
        return isRunning();
    }

    /** 内核 REST API 探活 */
    private boolean probeApi() {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL(getApiBase() + "/version").openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            c.setRequestProperty("Authorization", "Bearer " + apiSecret);
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean isRunning() {
        return proc != null;
    }

    /** 停止内核：先优雅退出再强杀，防僵尸进程（RISK_CHECKLIST 技术风险项） */
    public synchronized void stop() {
        if (proc != null) {
            try {
                proc.destroy();
                try {
                    if (!proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                        proc.destroyForcibly();
                    }
                } catch (InterruptedException ie) {
                    proc.destroyForcibly();
                }
            } catch (Throwable t) {
                // ignore
            }
            proc = null;
        }
    }
}
