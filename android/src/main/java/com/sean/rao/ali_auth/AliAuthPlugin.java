package com.sean.rao.ali_auth;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.alibaba.fastjson.JSONObject;
import com.hjq.toast.CustomToast;
import com.hjq.toast.ToastStrategy;
import com.hjq.toast.ToastUtils;
import com.hjq.toast.config.IToast;
import com.mobile.auth.gatewayauth.PhoneNumberAuthHelper;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import com.sean.rao.ali_auth.login.OneKeyLoginPublic;
import com.sean.rao.ali_auth.utils.UtilTool;

import java.util.concurrent.atomic.AtomicBoolean;

/** AliAuthPlugin */
public class AliAuthPlugin extends FlutterActivity implements FlutterPlugin, ActivityAware, MethodCallHandler, EventChannel.StreamHandler {
  private static final String TAG = "ali_auth 一键登录插件";

  private Context mContext;
  private Activity mActivity;
  /// The MethodChannel that will the communication between Flutter and native Android
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;

  private FlutterEngine flutterEngine;

  private FlutterEngineCache flutterEngineCache;

  public static EventChannel.EventSink _events;

  private static final String METHOD_CHANNEL = "ali_auth";
  private static final String EVENT_CHANNEL = "ali_auth/event";

  private ConnectivityManager.NetworkCallback callback;

  /**
   * 延时登录
   */
  private OneKeyLoginPublic oneKeyLoginPublic;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), METHOD_CHANNEL);

    // 原生通讯
    EventChannel eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), EVENT_CHANNEL);
    eventChannel.setStreamHandler(this);
//    // Activity
//    flutterView.getLookupKeyForAsset("images/ic_launcher.png");
//    // Fragment
//    (FlutterView) getView().getLookupKeyForAsset("images/ic_launcher.png");
//    // 通用
//    FlutterMain.getLookupKeyForAsset("images/ic_launcher.png");

    // 创建flutter发动机
    flutterEngineCache = FlutterEngineCache.getInstance();
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method){
      case "getPlatformVersion":
        result.success("当前Android信息：" + android.os.Build.VERSION.RELEASE);
        break;
      case "initSdk":
        if (_events == null) {
          result.error("500001", "请先对插件进行监听！", null);
        } else {
          boolean isDelay = (boolean) call.argument("isDelay");
          /// 判断是否初始化过或者是否是同步登录，如果是将进行再次初始化
          if (oneKeyLoginPublic == null || !isDelay) {
            oneKeyLoginPublic = new OneKeyLoginPublic(mActivity, _events, call.arguments);
          }
        }
        break;
      case "login":
        if (oneKeyLoginPublic != null) {
          oneKeyLoginPublic.startLogin((int) call.argument("timeout"));
        } else {
          // result.error("500002", "该接口为延时登录接口，请先初始化后再次调用该接口！", null);
          _events.success(UtilTool.resultFormatData("500003", null, ""));
        }
        break;
      case "checkEnvAvailable":
        oneKeyLoginPublic.checkEnvAvailable(2);
        break;
      case "checkCellularDataEnable":
        isNetEnabled(mContext, result);
        break;
      case "quitPage":
        oneKeyLoginPublic.quitPage();
        break;
      case "openPage":
        if (flutterEngine == null) {
          flutterEngine = new FlutterEngine(mContext);
        }
        //指定想要跳转的flutter页面 这里要和下图对应上 记住他
        flutterEngine.getNavigationChannel().setInitialRoute(call.argument("pageRoute"));
        flutterEngine.getDartExecutor().executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault());
        //这里做一个缓存 可以在适当的地方执行他 比如MyApp里 或者未跳转flutterr之前 在flutter页面执行前预加载
        //缓存可以缓存好多个 以不同的的id区分
        flutterEngineCache.put("default_engine_id", flutterEngine);
        //上面的代码一般在执行跳转操作之前调用 这样可以预加载页面 是的跳转的时候速度加快
        //跳转页面
        mActivity.startActivity(FlutterActivity.withCachedEngine("default_engine_id").build(mContext));
        result.success("调用成功！");
        break;
      default:
        result.notImplemented();
    }
  }

  @Override
  public void onListen(Object o, EventChannel.EventSink eventSink) {
    Log.d(TAG, "listen 初始化完毕！");
    String version = PhoneNumberAuthHelper.getVersion();
    eventSink.success(UtilTool.resultFormatData("500004", String.format("插件启动监听成功, 当前SDK版本: %s", version), ""));
    if( _events == null ){
      _events = eventSink;
      // init();
      // implementation 'com.github.getActivity:ToastUtils:10.3'
      ToastUtils.init(mActivity.getApplication(), new ToastStrategy() {
        @Override
        public IToast createToast(Application application) {
          IToast toast = super.createToast(application);
          CustomToast customToast = ((CustomToast) toast);
          // 设置 Toast 动画效果
          //customToast.setAnimationsId(R.anim.xxx);
          // 设置短 Toast 的显示时长（默认是 2000 毫秒）
          customToast.setShortDuration(1000);
            // 设置长 Toast 的显示时长（默认是 3500 毫秒）
          customToast.setLongDuration(5000);
          toast.setMargin(toast.getHorizontalMargin(), 10);
          return toast;
        }
      });
    }
  }

  @Override
  public void onCancel(Object o) {
    if( _events != null){
      _events = null;
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    mActivity = binding.getActivity();
    mContext = mActivity.getBaseContext();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

  }

  @Override
  public void onDetachedFromActivity() {
    if( _events != null){
      _events.endOfStream();
    }
    mActivity = null;
  }

  /**
   * 判断移动网络是否开启
   *
   * @param context
   * @return
   */
  public void isNetEnabled(Context context, @NonNull Result result) {
    JSONObject resultObject = new JSONObject();
    resultObject.put("code", 0);
    resultObject.put("msg", "未检测到网络！");
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//      callback = new ConnectivityManager.NetworkCallback() {
//        // 可用网络接入
//        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
//          // 一般在此处获取网络类型然后判断网络类型，就知道时哪个网络可以用connected
//          System.out.println(network);
//          System.out.println(networkCapabilities);
//        }
//        // 网络断开
//        public void onLost(Network network) {
//          System.out.println(network);
//          // 如果通过ConnectivityManager#getActiveNetwork()返回null，表示当前已经没有其他可用网络了。
//        }
//      };
//      registerNetworkCallback(context);

      Network network =cm.getActiveNetwork();
      if(network!=null){
        NetworkCapabilities nc=cm.getNetworkCapabilities(network);
        if(nc!=null){
          resultObject.put("code", 1);
          if(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){ // WIFI
            resultObject.put("msg", "WIFI网络已开启");
          }else if(nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)){ // 移动数据
            resultObject.put("msg", "蜂窝网络已开启");
          }
        }
      }
    } else {
      NetworkInfo mWiFiNetworkInfo = cm.getActiveNetworkInfo();
      if (mWiFiNetworkInfo != null) {
        resultObject.put("code", 1);
        if (mWiFiNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI) { // WIFI
          resultObject.put("msg", "WIFI网络已开启");
        } else if (mWiFiNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) { // 移动数据
          resultObject.put("msg", "蜂窝网络已开启");
        }
      }
    }
    result.success(resultObject);
  }


  // 注册回调
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void registerNetworkCallback(Context context) {
    ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkRequest.Builder builder = new NetworkRequest.Builder();
    builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
    builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
    cm.registerNetworkCallback(builder.build(), callback);
  }



  // 注销回调
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void unregisterNetworkCallback(Context context) {
    ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    cm.unregisterNetworkCallback(callback);
  }

  /**
   * 判断移动网络是否连接成功
   *
   * @param context
   * @return
   */
  public boolean isNetContected(Context context) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
    if (cm != null && info != null && info.isConnected()) {
      Log.i(TAG, "移动网络连接成功");
      return true;
    }
    Log.i(TAG, "移动网络连接失败");
    return false;
  }
}
