package com.example.autorun.worker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.autorun.ui.login.LoginActivity;

import org.runrun.entity.AppConfig;
import com.example.autorun.helper.App;

import java.time.LocalTime;

/**
 * Periodic worker that triggers auto sign-in/sign-back in configured time windows.
 */
public class AutoSignWorker extends Worker {

    public AutoSignWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            SharedPreferences sp = context.getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE);

            if (!sp.getBoolean(LoginActivity.AUTO_SIGN_ENABLED, false)) {
                cancelSelf();
                return Result.success();
            }

            String phone = sp.getString("phone", null);
            String password = sp.getString("password", null);
            if (phone == null || password == null || phone.trim().isEmpty() || password.trim().isEmpty()) {
                return Result.success();
            }

            if (!isInAutoWindow(LocalTime.now())) {
                return Result.success();
            }

            AppConfig appConfig = new AppConfig();
            appConfig.setPhone(phone);
            appConfig.setPassword(password);
            if (appConfig.getToken() == null) {
                appConfig.setToken(new StringBuffer());
            }

            App app = new App(appConfig);
            // 在窗口内执行轮询尝试：每3分钟一次，最多4次（约12分钟窗口覆盖）
            for (int i = 0; i < 4; i++) {
                String result = app.runSignInOrBackForWorker();

                if ("AUTH_FAIL".equals(result)) {
                    sp.edit().putBoolean(LoginActivity.AUTO_SIGN_ENABLED, false).apply();
                    cancelSelf();
                    return Result.success();
                }

                if ("SUCCESS".equals(result) || "NO_PENDING".equals(result) || "ALREADY_DONE".equals(result)) {
                    sp.edit().putBoolean(LoginActivity.AUTO_SIGN_ENABLED, false).apply();
                    cancelSelf();
                    return Result.success();
                }

                if ("OUT_OF_WINDOW".equals(result)) {
                    return Result.success();
                }

                if (i < 3) {
                    Thread.sleep(3 * 60 * 1000L);
                }
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void cancelSelf() {
        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork("auto_sign_task");
    }

    private boolean isInAutoWindow(LocalTime now) {
        return inWindow(now, LocalTime.of(7, 50), LocalTime.of(8, 0))
                || inWindow(now, LocalTime.of(17, 50), LocalTime.of(18, 0))
                || inWindow(now, LocalTime.of(8, 50), LocalTime.of(9, 0))
                || inWindow(now, LocalTime.of(18, 50), LocalTime.of(19, 0));
    }

    private boolean inWindow(LocalTime now, LocalTime start, LocalTime end) {
        return !now.isBefore(start) && !now.isAfter(end);
    }
}
