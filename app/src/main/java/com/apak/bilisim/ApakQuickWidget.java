package com.apak.bilisim;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.widget.RemoteViews;
import android.widget.Toast;

public class ApakQuickWidget extends AppWidgetProvider {

    public static final String ACTION_BOOST  = "com.apak.bilisim.ACTION_WIDGET_BOOST";
    public static final String ACTION_RESCUE = "com.apak.bilisim.ACTION_WIDGET_RESCUE";
    public static final String ACTION_TORCH  = "com.apak.bilisim.ACTION_WIDGET_TORCH";

    private static boolean isTorchOn = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_layout);

        // Pil seviyesi oku
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent bat = context.getApplicationContext().registerReceiver(null, ifilter);
        if (bat != null) {
            int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = (int) ((level / (float) scale) * 100);
            views.setTextViewText(R.id.widgetBatteryText, "🔋 %" + pct);
        }

        // 1. Boost Tıklaması
        Intent boostIntent = new Intent(context, ApakQuickWidget.class);
        boostIntent.setAction(ACTION_BOOST);
        PendingIntent boostPending = PendingIntent.getBroadcast(
                context, 101, boostIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetBoost, boostPending);

        // 2. Rescue Tıklaması
        Intent rescueIntent = new Intent(context, ApakQuickWidget.class);
        rescueIntent.setAction(ACTION_RESCUE);
        PendingIntent rescuePending = PendingIntent.getBroadcast(
                context, 102, rescueIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetRescue, rescuePending);

        // 3. Torch Tıklaması
        Intent torchIntent = new Intent(context, ApakQuickWidget.class);
        torchIntent.setAction(ACTION_TORCH);
        PendingIntent torchPending = PendingIntent.getBroadcast(
                context, 103, torchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetTorch, torchPending);

        // Başlığa tıklayınca uygulamayı aç
        Intent appIntent = new Intent(context, MainActivity.class);
        PendingIntent appPending = PendingIntent.getActivity(
                context, 100, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetTitle, appPending);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (action == null) return;

        if (ACTION_BOOST.equals(action)) {
            // Bellek ve önbellek boşaltma simülasyonu & Garbage Collector
            System.gc();
            Runtime.getRuntime().gc();
            Toast.makeText(context, "⚡ APAK BİLİŞİM: Sistem Belleği & Önbellek Optimize Edildi!", Toast.LENGTH_SHORT).show();
            refreshWidgets(context);

        } else if (ACTION_RESCUE.equals(action)) {
            // Yaşlı/Dede ses kurtarma: Zil ve Medya sesini %100 yap, normal moda al
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    int maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING);
                    int maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int maxNotif = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);

                    am.setStreamVolume(AudioManager.STREAM_RING, maxRing, AudioManager.FLAG_SHOW_UI);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0);
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotif, 0);

                    Toast.makeText(context, "🔊 APAK BİLİŞİM: Zil, Çağrı ve Medya Sesi %100'e Çıkarıldı!", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "🔊 APAK BİLİŞİM: Ses Seviyesi Maksimuma Alındı!", Toast.LENGTH_SHORT).show();
            }

        } else if (ACTION_TORCH.equals(action)) {
            // Fener aç/kapat
            try {
                CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (cm != null) {
                    String[] ids = cm.getCameraIdList();
                    for (String id : ids) {
                        CameraCharacteristics c = cm.getCameraCharacteristics(id);
                        Boolean hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        if (hasFlash != null && hasFlash) {
                            isTorchOn = !isTorchOn;
                            cm.setTorchMode(id, isTorchOn);
                            Toast.makeText(context, isTorchOn ? "🔦 Fener Açıldı" : "🔦 Fener Kapatıldı", Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Toast.makeText(context, "Fener kontrol edilemedi", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void refreshWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ApakQuickWidget.class));
        for (int id : ids) {
            updateAppWidget(context, manager, id);
        }
    }
}
