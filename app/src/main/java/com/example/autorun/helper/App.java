package com.example.autorun.helper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.TintContextWrapper;

import com.example.autorun.ui.login.LoginActivity;
import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.hc.core5.http.ParseException;
import org.runrun.entity.AppConfig;
import org.runrun.entity.Location;
import org.runrun.entity.NewRecordBody;
import org.runrun.entity.Response;
import org.runrun.entity.ResponseType.NewRecordResult;
import org.runrun.entity.ResponseType.RunStandard;
import org.runrun.entity.ResponseType.SchoolBound;
import org.runrun.entity.ResponseType.SignInTf;
import org.runrun.entity.ResponseType.UserInfo;
import org.runrun.entity.SignInOrSignBackBody;
import org.runrun.run.Request;
import org.runrun.utils.FileUtil;
import org.runrun.utils.JsonUtils;
import org.runrun.utils.TrackUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Date;

import lombok.Setter;

/**
 * 主业务线程
 */
public class App extends Thread {
    AppConfig config;

    @Setter
    private InputStream mapInput;

    @Setter
    private TextView resultArea;

    @Setter
    ProgressBar loadingProgressBar;

    public static String ERROR;

    @Setter
    private String type;

    private final StringBuffer token;

    public App(AppConfig config) {
        this.config = config;
        token = config.getToken();
    }

    // 跑步
    @SuppressLint("DefaultLocale")
    public void runRun() throws IOException, ParseException {
        appendMsg("开始");
        // ==========配置 START==============
        String phone = config.getPhone();
        String password = config.getPassword();
        int schoolSite = 0; // 0航空港，1龙泉暂不支持
        long runDistance = config.getDistance(); // 路程米
        int runTime = config.getRunTime(); // 时间分钟
        // ==========配置 END==============

        if (config.getBrand().length() == 0) {
            appendMsg("请配置手机型号信息");
            return;
        }

        // 计算平均配速，防止跑太快
        double average = 1.0 * runTime / runDistance * 1000;

        if (Double.isNaN(average)) {
            appendMsg("输入不正确");
            return;
        }
        if (average < 6) {
            String[] notice = {
                    "我认为这种事情是不可能的",
                    "太快了",
                    "要死了",
                    "你正在自毁",
                    "你正在自残",
                    "你得锻炼正造成身体上的损伤",
                    "六分是养身",
                    "七分是自娱",
                    "八分是治愈"
            };
            appendMsg("八分是治愈，七分是自娱，六分是养身，五分是自伤，四分是自残，三分是自毁。");
            appendMsg(String.format("你的配速是：%.2f 分钟/公里, %s", average, notice[(int) average]));
            return;
        }
        appendMsg(String.format("平均配速：%.2f\n", average));

        Request request = new Request(token.toString(), config);
        appendMsg("开始登录");
        Response<UserInfo> userInfoResponse = request.login(phone, password);
        if (userInfoResponse == null || userInfoResponse.getResponse() == null) {
            appendMsg("登录失败");
            return;
        }

        UserInfo userInfo = userInfoResponse.getResponse();
        long userId = userInfo.getUserId();
        if (userId == -1) {
            appendMsg("用户Id获取失败");
            return;
        }

        token.delete(0, token.length());
        token.append(request.getToken());

        appendMsg("获取跑步标准");
        RunStandard runStandard = request.getRunStandard(userInfo.getSchoolId());
        appendMsg("获取学校经纬度区域信息");
        SchoolBound[] schoolBounds = request.getSchoolBound(userInfo.getSchoolId());

        appendMsg("生成跑步数据");
        NewRecordBody recordBody = new NewRecordBody();
        recordBody.setUserId(userId);
        recordBody.setAppVersions(config.getAppVersion());
        recordBody.setBrand(config.getBrand());
        recordBody.setMobileType(config.getMobileType());
        recordBody.setSysVersions(config.getSysVersion());
        recordBody.setRunDistance(runDistance);
        recordBody.setRunTime(runTime);
        recordBody.setYearSemester(runStandard.getSemesterYear());
        recordBody.setRealityTrackPoints(schoolBounds[schoolSite].getSiteBound() + "--");

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.applyPattern("yyyy-MM-dd");
        Date date = new Date();
        String formatTime = sdf.format(date);
        recordBody.setRecordDate(formatTime);

        String tack = genTack(runDistance);
        recordBody.setTrackPoints(tack);

        appendMsg("提交跑步数据");
        String result = request.recordNew(recordBody);
        Response<NewRecordResult> response = JsonUtils.string2Obj(result, new TypeReference<Response<NewRecordResult>>() {});
        appendMsg("");
        appendMsg("返回原始数据：" + result);
        appendMsg("解析数据：");
        appendMsg("跑步结果：" + response.getCode() + " - " + response.getMsg());

        NewRecordResult response1 = response.getResponse();
        if (response1 != null) {
            appendMsg("生成的跑步ID：" + response1.getRecordId());
            appendMsg("结果状态：" + response1.getResultStatus());
            appendMsg("结果描述：" + response1.getResultDesc());
            appendMsg("超速警告次数：" + response1.getOverSpeedWarn());
            appendMsg("警告内容：" + response1.getWarnContent());
        }
    }

    // 签到/签退
    public void runSignInOrBack() throws IOException {
        String result = runSignInOrBackForWorker();
        appendMsg("签到签退结果：" + result);
    }

    public void run() {
        try {
            if ("run".equals(type)) {
                runRun();
            } else if ("signInOrBack".equals(type)) {
                runSignInOrBack();
            } else {
                appendMsg("未知操作");
            }
        } catch (Exception e) {
            e.printStackTrace();
            String msg;
            if (e instanceof RuntimeException) {
                StackTraceElement traceElement = e.getStackTrace()[0];
                msg = e.getMessage() + "\n异常来源：" + traceElement.getClassName() + " - line:" + traceElement.getLineNumber();
            } else {
                msg = e.getMessage();
            }
            appendMsg(msg);
        } finally {
            stopLoading();
        }
    }

    public void appendMsg(String msg) {
        if (resultArea == null) return;
        Context context = resultArea.getContext();

        Activity activity = null;
        if (context instanceof LoginActivity) {
            activity = (Activity) context;
        } else if (context instanceof TintContextWrapper) {
            activity = (Activity) ((TintContextWrapper) context).getBaseContext();
        }

        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultArea.append("\n" + msg);
                }
            });
        }
    }

    public String genTack(long distance) {
        if (mapInput == null) {
            mapInput = org.runrun.App.class.getResourceAsStream("/map.json");
        }
        String json = FileUtil.ReadFile(mapInput);
        try {
            mapInput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (json.length() == 0) {
            System.out.println("配置读取失败");
            return null;
        }
        Location[] locations = JsonUtils.string2Obj(json, Location[].class);
        return TrackUtils.gen(distance, locations);
    }

    public void stopLoading() {
        if (resultArea == null || loadingProgressBar == null) return;
        Context context = resultArea.getContext();

        Activity activity = null;
        if (context instanceof LoginActivity) {
            activity = (Activity) context;
        } else if (context instanceof TintContextWrapper) {
            activity = (Activity) ((TintContextWrapper) context).getBaseContext();
        }

        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadingProgressBar.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    /**
     * 旧时间窗逻辑保留，仅用于日志提示，不阻断自动流程。
     */
    private String resolveActionByWindow(LocalTime now) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (inWindow(now, LocalTime.of(7, 50), LocalTime.of(8, 0))
                    || inWindow(now, LocalTime.of(17, 50), LocalTime.of(18, 0))) {
                return "signIn";
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (inWindow(now, LocalTime.of(8, 50), LocalTime.of(9, 0))
                    || inWindow(now, LocalTime.of(18, 50), LocalTime.of(19, 0))) {
                return "signBack";
            }
        }
        return null;
    }

    private boolean inWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return false;
    }

    /**
     * 自动签到/签退核心：
     * 1. 以服务端状态为准自动推断 signType，避免时间窗导致不触发。
     * 2. 仍保留时间窗日志，便于排错。
     */
    public String runSignInOrBackForWorker() throws IOException {
        String phone = config.getPhone();
        String password = config.getPassword();
        Request request = new Request(token.toString(), config);

        Response<UserInfo> loginResp = request.login(phone, password);
        if (loginResp == null || loginResp.getResponse() == null) {
            return "AUTH_FAIL";
        }

        token.delete(0, token.length());
        token.append(request.getToken());

        UserInfo userInfo = loginResp.getResponse();
        Long studentId = userInfo.getStudentId();
        if (studentId == null) {
            return "AUTH_FAIL";
        }

        SignInTf signInTf = request.getSignInTf(String.valueOf(studentId));
        if (signInTf == null) return "NO_PENDING";

        String signStatus = signInTf.getSignStatus();
        String signInStatus = signInTf.getSignInStatus();
        String signBackStatus = signInTf.getSignBackStatus();

        // 调试信息：时间窗只用于提示，不用于硬拦截
        String windowAction = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowAction = resolveActionByWindow(LocalTime.now());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appendMsg("状态: signStatus=" + signStatus
                    + ", signInStatus=" + signInStatus
                    + ", signBackStatus=" + signBackStatus
                    + ", windowAction=" + windowAction
                    + ", now=" + LocalTime.now());
        }

        if ("1".equals(signInStatus) && "1".equals(signBackStatus)) {
            return "ALREADY_DONE";
        }

        String signType = null;

        // 优先签退：已签入且当前处于可签退状态
        if ("1".equals(signInStatus) && "2".equals(signStatus) && !"1".equals(signBackStatus)) {
            signType = "2";
        }
        // 其次签到：当前处于可签到状态，且还未签到
        else if ("1".equals(signStatus) && !"1".equals(signInStatus)) {
            signType = "1";
        }

        if (signType == null) {
            return "NO_PENDING";
        }

        SignInOrSignBackBody body = new SignInOrSignBackBody(
                signInTf.getActivityId(),
                signInTf.getLatitude(),
                signInTf.getLongitude(),
                signType,
                studentId
        );

        Response signResp = request.signInOrSignBack(body);
        if (signResp == null) return "RETRY";

        if (signResp.getCode() == 10000) {
            return "2".equals(signType) ? "SUCCESS_SIGN_BACK" : "SUCCESS_SIGN_IN";
        }
        return "REMOTE_FAIL_" + signResp.getCode() + "_" + signResp.getMsg();
    }
}
