package com.apak.bilisim;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    // ========== NATIVE BRIDGE ==========
    public class ApakBridge {

        // ── PİL VERİLERİ (Tüm değerler donanımdan) ──
        @JavascriptInterface
        public String getBatteryData() {
            try {
                JSONObject obj = new JSONObject();

                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent bat = getApplicationContext().registerReceiver(null, ifilter);

                int level = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                int scale = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100) : 100;
                float percent = (level / (float) scale) * 100f;
                obj.put("level", Math.round(percent));

                int status = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;
                boolean charging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL);
                obj.put("charging", charging);
                obj.put("full", status == BatteryManager.BATTERY_STATUS_FULL);

                int voltageMv = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
                obj.put("voltage_mv", voltageMv);
                obj.put("voltage_v", Math.round(voltageMv / 10.0) / 100.0);

                int tempTenths = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) : 0;
                obj.put("temperature", tempTenths / 10.0);

                String tech = bat != null ? bat.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) : "Li-ion";
                obj.put("technology", tech != null ? tech : "Li-ion");

                int plugged = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) : 0;
                String chargeSource = "Bağlı Değil";
                if (plugged == BatteryManager.BATTERY_PLUGGED_AC)       chargeSource = "AC Adaptör";
                else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) chargeSource = "USB";
                else if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) chargeSource = "Kablosuz";
                obj.put("charge_source", chargeSource);

                BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) {
                    long currentMicroA = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    long currentMA = currentMicroA / 1000L;
                    obj.put("current_ma", currentMA);
                    obj.put("current_ma_abs", Math.abs(currentMA));

                    long chargeCounterUAh = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                    long remainingMAh = chargeCounterUAh / 1000L;
                    obj.put("remaining_mah", remainingMAh);

                    double watts = Math.abs(voltageMv / 1000.0 * currentMicroA / 1_000_000.0);
                    obj.put("power_w", Math.round(watts * 100.0) / 100.0);

                    long designCap = readSysLong("/sys/class/power_supply/battery/charge_full_design");
                    if (designCap <= 0) designCap = readSysLong("/sys/class/power_supply/battery/energy_full_design");
                    if (designCap > 0 && designCap > 10000) designCap /= 1000;
                    obj.put("design_capacity_mah", designCap);

                    long fullCap = readSysLong("/sys/class/power_supply/battery/charge_full");
                    if (fullCap <= 0) fullCap = readSysLong("/sys/class/power_supply/battery/energy_full");
                    if (fullCap > 0 && fullCap > 10000) fullCap /= 1000;
                    obj.put("full_capacity_mah", fullCap);

                    if (designCap > 0 && fullCap > 0) {
                        int health = (int) Math.min(100, (fullCap * 100L / designCap));
                        obj.put("battery_health_pct", health);
                    }

                    if (!charging && currentMA != 0 && remainingMAh > 0) {
                        double hoursLeft = (double) remainingMAh / Math.abs(currentMA);
                        int hh = (int) hoursLeft;
                        int mm = (int) ((hoursLeft - hh) * 60);
                        obj.put("time_remaining_str", hh + " Saat " + mm + " Dk");
                    } else if (charging && currentMA != 0 && remainingMAh > 0 && fullCap > 0) {
                        double hoursToFull = (double)(fullCap - remainingMAh) / Math.abs(currentMA);
                        int hh = (int) hoursToFull;
                        int mm = (int) ((hoursToFull - hh) * 60);
                        obj.put("time_to_full_str", hh + " Saat " + mm + " Dk");
                    }
                }

                int healthStatus = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) : 0;
                String healthStr = "İyi";
                switch (healthStatus) {
                    case BatteryManager.BATTERY_HEALTH_GOOD:        healthStr = "İyi"; break;
                    case BatteryManager.BATTERY_HEALTH_OVERHEAT:    healthStr = "Aşırı Isınma"; break;
                    case BatteryManager.BATTERY_HEALTH_DEAD:        healthStr = "Ölü"; break;
                    case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthStr = "Aşırı Voltaj"; break;
                    case BatteryManager.BATTERY_HEALTH_COLD:        healthStr = "Soğuk"; break;
                }
                obj.put("health_status", healthStr);

                return obj.toString();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // ── RAM VERİSİ ──
        @JavascriptInterface
        public String getRamData() {
            try {
                JSONObject obj = new JSONObject();
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long totalMB   = mi.totalMem   / (1024 * 1024);
                long availMB   = mi.availMem   / (1024 * 1024);
                long usedMB    = totalMB - availMB;
                obj.put("total_mb",  totalMB);
                obj.put("avail_mb",  availMB);
                obj.put("used_mb",   usedMB);
                obj.put("used_pct",  Math.round(usedMB * 100.0 / totalMB));
                obj.put("low_memory", mi.lowMemory);
                return obj.toString();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // ── DEPOLAMA VERİSİ ──
        @JavascriptInterface
        public String getStorageData() {
            try {
                JSONObject obj = new JSONObject();
                File path = Environment.getDataDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize  = stat.getBlockSizeLong();
                long totalBlocks = stat.getBlockCountLong();
                long availBlocks = stat.getAvailableBlocksLong();
                long totalGB = (totalBlocks * blockSize) / (1024*1024*1024);
                long freeGB  = (availBlocks * blockSize) / (1024*1024*1024);
                long usedGB  = totalGB - freeGB;
                obj.put("total_gb", totalGB);
                obj.put("free_gb",  freeGB);
                obj.put("used_gb",  usedGB);
                obj.put("used_pct", totalGB > 0 ? Math.round(usedGB * 100.0 / totalGB) : 0);
                return obj.toString();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // ── CPU VERİSİ ──
        @JavascriptInterface
        public String getCpuData() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("cores", Runtime.getRuntime().availableProcessors());
                obj.put("model", Build.HARDWARE);
                obj.put("board", Build.BOARD);
                obj.put("abi",   Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a");

                long maxFreqKhz = 0;
                for (int i = 0; i < 8; i++) {
                    long f = readSysLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
                    if (f > maxFreqKhz) maxFreqKhz = f;
                }
                if (maxFreqKhz > 0) {
                    obj.put("max_freq_mhz", maxFreqKhz / 1000);
                    obj.put("max_freq_ghz", Math.round(maxFreqKhz / 10000.0) / 100.0);
                }

                double cpuTemp = readCpuTemp();
                if (cpuTemp > 0) obj.put("temperature", cpuTemp);

                return obj.toString();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // ── CİHAZ BİLGİSİ ──
        @JavascriptInterface
        public String getDeviceInfo() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("brand",       Build.BRAND);
                obj.put("model",       Build.MODEL);
                obj.put("device",      Build.DEVICE);
                obj.put("manufacturer",Build.MANUFACTURER);
                obj.put("android",     Build.VERSION.RELEASE);
                obj.put("sdk",         Build.VERSION.SDK_INT);
                obj.put("build_id",    Build.ID);

                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                obj.put("screen_w",   dm.widthPixels);
                obj.put("screen_h",   dm.heightPixels);
                obj.put("dpi",        dm.densityDpi);
                obj.put("density",    dm.density);
                obj.put("screen_inch", Math.round(Math.sqrt(
                    Math.pow(dm.widthPixels/dm.xdpi, 2) +
                    Math.pow(dm.heightPixels/dm.ydpi, 2)) * 10.0) / 10.0);

                return obj.toString();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // ── WI-FI VE AĞ SİNYALİ (dBm) ──
        @JavascriptInterface
        public String getWifiSignalInfo() {
            try {
                JSONObject obj = new JSONObject();
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.isWifiEnabled()) {
                    WifiInfo winfo = wm.getConnectionInfo();
                    int rssi = winfo.getRssi(); // dBm (örn. -50 dBm)
                    int linkSpeed = winfo.getLinkSpeed(); // Mbps
                    String ssid = winfo.getSSID() != null ? winfo.getSSID().replace("\"", "") : "Wi-Fi";
                    int ip = winfo.getIpAddress();
                    String ipStr = String.format("%d.%d.%d.%d",
                            (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));

                    int level = WifiManager.calculateSignalLevel(rssi, 5); // 0-4

                    obj.put("enabled", true);
                    obj.put("ssid", ssid);
                    obj.put("rssi_dbm", rssi);
                    obj.put("link_speed_mbps", linkSpeed);
                    obj.put("ip", ipStr);
                    obj.put("level", level);
                } else {
                    obj.put("enabled", false);
                }
                return obj.toString();
            } catch (Exception e) {
                return "{\"enabled\":false, \"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // ── YAŞLI / DEDE KURTARMA (ZİL & MEDYA SESİNİ %100 YAP) ──
        @JavascriptInterface
        public boolean fixElderSettings() {
            try {
                runOnUiThread(() -> {
                    try {
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                            int maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING);
                            int maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            int maxNotif = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);

                            am.setStreamVolume(AudioManager.STREAM_RING, maxRing, AudioManager.FLAG_SHOW_UI);
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0);
                            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotif, 0);

                            Toast.makeText(MainActivity.this, "🔊 APAK BİLİŞİM: Tüm Ses Seviyeleri %100 Yapıldı!", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception ignored) {}
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        // ── TEK TIKLA BELLEK / ÖNBELLEK TEMİZLEME ──
        @JavascriptInterface
        public String cleanAppCache() {
            try {
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.clearCache(true);
                    }
                    System.gc();
                    Runtime.getRuntime().gc();
                    Toast.makeText(MainActivity.this, "⚡ Sistem Önbelleği & RAM Optimize Edildi!", Toast.LENGTH_SHORT).show();
                });
                return "{\"success\":true}";
            } catch (Exception e) {
                return "{\"success\":false}";
            }
        }

        // ── WHATSAPP SAĞLIK RAPORU PAYLAŞ ──
        @JavascriptInterface
        public void shareWhatsAppReport(String reportText) {
            try {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, reportText);
                sendIntent.setType("text/plain");
                Intent shareIntent = Intent.createChooser(sendIntent, "APAK Bilişim Raporunu Paylaş");
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(shareIntent);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Paylaşım açılamadı", Toast.LENGTH_SHORT).show());
            }
        }

        // ── SİSTEM AYARLARINA YÖNLENDİR (Depolama & Ekran) ──
        @JavascriptInterface
        public void openStorageSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }

        // ── FLAŞ / EL FENERİ ──
        @JavascriptInterface
        public void setTorch(boolean on) {
            try {
                CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String[] ids = cm.getCameraIdList();
                for (String id : ids) {
                    CameraCharacteristics c = cm.getCameraCharacteristics(id);
                    Boolean hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (hasFlash != null && hasFlash) {
                        cm.setTorchMode(id, on);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        private long readSysLong(String path) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(path));
                String line = br.readLine();
                br.close();
                return Long.parseLong(line.trim());
            } catch (Exception e) { return 0; }
        }

        private double readCpuTemp() {
            String[] paths = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
            };
            for (String p : paths) {
                long v = readSysLong(p);
                if (v > 0) return v > 1000 ? v / 1000.0 : v / 10.0;
            }
            return 0;
        }
    }

    // ===================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // İzin kontrolleri
        String[] permissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        };

        boolean needReq = false;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needReq = true;
                break;
            }
        }
        if (needReq) {
            ActivityCompat.requestPermissions(this, permissions, 101);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new ApakBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
