package com.example.autorun.helper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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
import java.util.*;
import java.time.LocalTime;

import lombok.Setter;

/**
 * Hello world!
 *
 */
public class App extends Thread
{
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

//    跑步
    @SuppressLint("DefaultLocale")
    public void runRun() throws IOException, ParseException {

        appendMsg("开始");
        // ==========配置 START==============
        String phone = config.getPhone();
        String password = config.getPassword();
        int schoolSite = 0;     // 0航空港，1龙泉暂不支持
        long runDistance = config.getDistance();        // 路程米
        int runTime = config.getRunTime();               // 时间分钟

        // 型号仓库： https://github.com/KHwang9883/MobileModels
        // ==========配置 END==============

        if (config.getBrand().length() == 0) {
            appendMsg("请配置手机型号信息");
            return;
        }
        // 计算平均配速，防止跑太快
        double average = 1.0 * runTime / runDistance * 1000;

        if(Double.isNaN(average)){
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

//        if(config.getRunTime() > 0)return;

        Request request = new Request(token.toString(), config);
        appendMsg("开始登录");
        Response<UserInfo> userInfoResponse = request.login(phone, password);
        UserInfo userInfo = userInfoResponse.getResponse();
        if(userInfo == null ) {
            appendMsg("登录失败");
            return;
        }
        long userId = userInfo.getUserId();
        if (userId != -1) {
            token.delete(0, token.length());
            token.append(request.getToken());

            appendMsg("获取跑步标准");
            RunStandard runStandard = request.getRunStandard(userInfo.getSchoolId());
            appendMsg("获取学校经纬度区域信息");
            SchoolBound[] schoolBounds = request.getSchoolBound(userInfo.getSchoolId());

            appendMsg("生成跑步数据");
            // 新增跑步数据
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

            // 今天日期 年-月-日
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat();
            sdf.applyPattern("yyyy-MM-dd");
            Date date = new Date();
            String formatTime = sdf.format(date);
            recordBody.setRecordDate(formatTime);

            // 生成跑步数据
            String tack = genTack(runDistance);
            recordBody.setTrackPoints(tack);

            //发送数据
            appendMsg("提交跑步数据");
            String result = request.recordNew(recordBody);
            Response<NewRecordResult> response = JsonUtils.string2Obj(result, new TypeReference<Response<NewRecordResult>>() {
            });
            appendMsg("");
            appendMsg("返回原始数据：" + result);
            appendMsg("解析数据：");
            appendMsg("跑步结果：" + response.getCode() + " - " + response.getMsg());
            NewRecordResult response1 = response.getResponse();
            appendMsg("生成的跑步ID：" + response1.getRecordId());
            appendMsg("结果状态：" + response1.getResultStatus());
            appendMsg("结果描述：" + response1.getResultDesc());
            appendMsg("超速警告次数：" + response1.getOverSpeedWarn());
            appendMsg("警告内容：" + response1.getWarnContent());
        } else {
            appendMsg("用户Id获取失败");
        }
    }

    // 签到/签退
    public void runSignInOrBack() throws IOException {
        String phone = config.getPhone();
        String password = config.getPassword();
        Request request = new Request(token.toString(), config);

        appendMsg("校验账号密码");
        Response<UserInfo> loginResp = request.login(phone, password);
        if (loginResp == null || loginResp.getResponse() == null) {
            appendMsg("账号或密码错误，无法开启自动签到/签退");
            return;
        }

        // 登录成功后刷新 token
        token.delete(0, token.length());
        token.append(request.getToken());

        UserInfo userInfo = loginResp.getResponse();
        Long studentId = userInfo.getStudentId();
        if (studentId == null) {
            appendMsg("登录信息不完整（studentId为空），无法自动签到/签退");
            return;
        }

        SignInTf signInTf = request.getSignInTf(String.valueOf(studentId));
        if (signInTf == null) {
            appendMsg("未查询到待签到俱乐部信息");
            return;
        }

        appendMsg("待签到俱乐部：" + signInTf.toString());

        String action = resolveActionByWindow(LocalTime.now());
        if (action == null) {
            appendMsg("当前不在自动签到/签退时间窗口");
            return;
        }

        String signStatus = signInTf.getSignStatus();
        String signInStatus = signInTf.getSignInStatus();
        String signBackStatus = signInTf.getSignBackStatus();

        if ("1".equals(signInStatus) && "1".equals(signBackStatus)) {
            appendMsg("今日签到与签退已完成，无需重复操作");
            return;
        }

        String signType;
        if ("signIn".equals(action)) {
            if ("1".equals(signStatus)) {
                signType = "1"; // 可签到
            } else {
                appendMsg("当前时间窗为签到，但无可签到任务");
                return;
            }
        } else {
            if ("1".equals(signInStatus) && "2".equals(signStatus)) {
                signType = "2"; // 可签退
            } else {
                appendMsg("当前时间窗为签退，但无可签退任务");
                return;
            }
        }

        SignInOrSignBackBody body = new SignInOrSignBackBody(
                signInTf.getActivityId(),
                signInTf.getLatitude(),
                signInTf.getLongitude(),
                signType,
                studentId
        );

        Response signInOrSignBack = request.signInOrSignBack(body);
        appendMsg("签到签退结果：");
        if (signInOrSignBack == null) {
            appendMsg("请求响应为空，请稍后重试");
            return;
        }
        appendMsg(signInOrSignBack.getMsg());
    }

    public void run(){
        try{
            if("run".equals(type)) {
                runRun();
            }else if("signInOrBack".equals(type)){
                runSignInOrBack();
            }else{
                appendMsg("未知操作");
            }
        }catch (Exception e){
            e.printStackTrace();
            String msg;
            if(e instanceof RuntimeException) {
                StackTraceElement traceElement = e.getStackTrace()[0];
                msg = e.getMessage() + "\n异常来源：" +traceElement.getClassName() + " - line:" + traceElement.getLineNumber();
            }else{
                msg = e.getMessage();
            }
            appendMsg(msg);
        }finally {
            stopLoading();
        }
    }

    public void appendMsg(String msg){
        if (resultArea == null) return;
        Context context = resultArea.getContext();

        Activity activity = null;
        if(context instanceof LoginActivity) {
            activity = (Activity) context;
        }else if(context instanceof TintContextWrapper){
            activity = (Activity)((TintContextWrapper) context).getBaseContext();
        }
        if(activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultArea.append("\n" + msg);
                }
            });
    }

    public String genTack(long distance) {
        if(mapInput == null)
            mapInput = org.runrun.App.class.getResourceAsStream("/map.json");
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

    public void stopLoading(){
        if (resultArea == null || loadingProgressBar == null) return;
        Context context = resultArea.getContext();
        // 4.4 TintContextWrapper
        // 5.1 LoginActivity

        Activity activity = null;
        if(context instanceof LoginActivity) {
            activity = (Activity) context;
        }else if(context instanceof TintContextWrapper){
            activity = (Activity)((TintContextWrapper) context).getBaseContext();
        }

        if(activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadingProgressBar.setVisibility(View.INVISIBLE);
                }
            });
    }
    private String resolveActionByWindow(LocalTime now) {
        // 签到窗口：07:50-08:00, 17:50-18:00
        if (inWindow(now, LocalTime.of(7, 50), LocalTime.of(8, 0))
                || inWindow(now, LocalTime.of(17, 50), LocalTime.of(18, 0))) {
            return "signIn";
        }

        // 签退窗口：08:50-09:00, 18:50-19:00
        if (inWindow(now, LocalTime.of(8, 50), LocalTime.of(9, 0))
                || inWindow(now, LocalTime.of(18, 50), LocalTime.of(19, 0))) {
            return "signBack";
        }
        return null;
    }

    private boolean inWindow(LocalTime now, LocalTime start, LocalTime end) {
        return !now.isBefore(start) && !now.isAfter(end);
    }
    public String runSignInOrBackForWorker() throws IOException {
        String phone = config.getPhone();
        String password = config.getPassword();
        Request request = new Request(token.toString(), config);

        // 先登录校验账号密码
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

        String action = resolveActionByWindow(LocalTime.now());
        if (action == null) return "OUT_OF_WINDOW";

        String signStatus = signInTf.getSignStatus();
        String signInStatus = signInTf.getSignInStatus();
        String signBackStatus = signInTf.getSignBackStatus();

        if ("1".equals(signInStatus) && "1".equals(signBackStatus)) {
            return "ALREADY_DONE";
        }

        String signType;
        if ("signIn".equals(action)) {
            if ("1".equals(signStatus)) {
                signType = "1";
            } else {
                return "NO_PENDING";
            }
        } else {
            if ("1".equals(signInStatus) && "2".equals(signStatus)) {
                signType = "2";
            } else {
                return "NO_PENDING";
            }
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
        return signResp.getCode() == 10000 ? "SUCCESS" : "REMOTE_FAIL_" + signResp.getCode();
    }
}
