package com.nursa.appsflyer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.appsflyer.attribution.AppsFlyerRequestListener;
import com.appsflyer.deeplink.DeepLink;
import com.appsflyer.deeplink.DeepLinkResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "Appsflyer",
    permissions = {
        @Permission(
            strings = { Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE }
        )
    }
)
public class AppsflyerPlugin extends Plugin {
    private static final String PLUGIN_TAG = "SegmentAppsFlyerPlugin";
    private static final String APPS_FLYER_UID_KEY = "appsFlyerUID";
    private static final String INITIALIZATION_ERROR_MESSAGE = "Segment is not initialized";
    private static boolean initialized = false;
    private static Analytics analytics;
    private static String appsFlyerUID;

    private static Analytics buildAnalyticsInstance(Context context, String writeKey, Boolean debug) {
        Analytics.Builder builder = new Analytics.Builder(context, writeKey)
                .use(AppsflyerIntegration.FACTORY)
                .logLevel(Boolean.TRUE.equals(debug) ? Analytics.LogLevel.DEBUG : Analytics.LogLevel.INFO)
                .trackApplicationLifecycleEvents()
                .trackDeepLinks();
        return builder.build();
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        if (intent != null) {
            getActivity().setIntent(intent);
        }
    }

    @Override
    public void load() {
        Log.i("Load", "Load");
        AppsflyerIntegration.conversionListener = new AppsFlyerConversionListener() {
            @Override
            public void onConversionDataSuccess(Map<String, Object> conversionData) {
                JSObject res = new JSObject();
                res.put(EventDataKeys.CALLBACK_NAME, "onConversionDataSuccess");
                JSObject data = new JSObject();
                for (String attrName : conversionData.keySet()) {
                    data.put(attrName, conversionData.get((attrName)));
                }
                res.put(EventDataKeys.DATA, data);
                AppsflyerPlugin.this.notifyListeners(EventNames.CONVERSION_CALLBACK, res);
            }

            @Override
            public void onConversionDataFail(String status) {
                JSObject eventData = new JSObject();
                eventData.put(EventDataKeys.CALLBACK_NAME, "onConversionDataFail");
                eventData.put(EventDataKeys.STATUS, status);
                AppsflyerPlugin.this.notifyListeners(EventNames.CONVERSION_CALLBACK, eventData);
            }

            @Override
            public void onAppOpenAttribution(Map<String, String> attributionData) {
                JSObject eventData = new JSObject();
                eventData.put(EventDataKeys.CALLBACK_NAME, "onAppOpenAttribution");
                JSObject data = new JSObject();
                for (String attrName : attributionData.keySet()) {
                    data.put(attrName, attributionData.get((attrName)));
                }
                eventData.put(EventDataKeys.DATA, data);
                AppsflyerPlugin.this.notifyListeners(EventNames.ON_APP_OPEN_ATTRIBUTION_CALLBACK, eventData);
            }

            @Override
            public void onAttributionFailure(String status) {
                JSObject res = new JSObject();
                res.put(EventDataKeys.CALLBACK_NAME, "onAttributionFailure");
                res.put(EventDataKeys.STATUS, status);
                AppsflyerPlugin.this.notifyListeners(EventNames.ON_APP_OPEN_ATTRIBUTION_CALLBACK, res);
            }
        };

        AppsflyerIntegration.deepLinkListener = (DeepLinkResult deepLinkResult) -> {
            Log.i("Load", "Deep link");
            JSObject eventData = new JSObject();
            DeepLinkResult.Status status = deepLinkResult.getStatus();
            DeepLinkResult.Error error = deepLinkResult.getError();
            DeepLink deepLink = deepLinkResult.getDeepLink();
            switch (status) {
                case NOT_FOUND:
                    eventData.put(EventDataKeys.STATUS, "NOT_FOUND");
                    break;
                case ERROR:
                    eventData.put(EventDataKeys.STATUS, "ERROR");
                    break;
                case FOUND:
                    eventData.put(EventDataKeys.STATUS, "FOUND");
            }
            if (error != null) {
                eventData.put(EventDataKeys.ERROR, error.toString());
            }
            if (deepLink != null) {
                try {
                    JSONObject jsonObject = new JSONObject((deepLink.toString()));
                    eventData.put(EventDataKeys.DEEP_LINK, JSObject.fromJSONObject(jsonObject));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            AppsflyerPlugin.this.notifyListeners(EventNames.ON_DEEP_LINK_CALLBACK, eventData);
        };
    }

    @PluginMethod
    public void initialize(PluginCall call) {
        Log.i("Load", "Initialize ### Current Thread: " + Thread.currentThread().getId());
        if (AppsflyerPlugin.initialized) {
            call.reject("Segment is already initialized");
            return;
        }
        String writeKey = call.getString("writeKey");
        if (writeKey == null) {
            call.reject("Write key not provided");
            return;
        }
        Boolean isDebug = call.getBoolean("isDebug", false);
        Context context = this.getContext();
        Log.i("Load", "Enter UI Thread ### Current Thread: " + Thread.currentThread().getId());

        this.getActivity().runOnUiThread(() -> {
            Log.i("Load", "Enter UI Thread ### Current Thread: " + Thread.currentThread().getId());
            if (AppsflyerPlugin.analytics != null) {
                AppsflyerPlugin.analytics.shutdown();
            }
            try {
                AppsflyerPlugin.analytics = AppsflyerPlugin.buildAnalyticsInstance(context, writeKey, isDebug);
                AppsflyerPlugin.analytics.onIntegrationReady(AppsflyerIntegration.FACTORY.key(), (Object instance) -> {
                    AppsFlyerLib appsFlyerLibInstance = AppsFlyerLib.getInstance();
                    AppsFlyerRequestListener listener = new AppsFlyerRequestListener() {
                        @Override
                        public void onSuccess() {
                            AppsflyerPlugin.initialized = true;
                            AppsflyerPlugin.appsFlyerUID = appsFlyerLibInstance.getAppsFlyerUID(context);
                            Log.i("Load", "Appsflyer Success ### Current Thread: " + Thread.currentThread().getId());
                            call.resolve();
                        }

                        @Override
                        public void onError(int i, @NonNull String s) {
                            Log.e("Load", "Appsflyer Error ### Current Thread: " + Thread.currentThread().getId());
                            call.reject("Appsflyer failed to initialize. Error code: " + i + "\n" + s);
                        }
                    };
                    appsFlyerLibInstance.start(context, null, listener);
                });
            } catch (Exception e) {
                Log.e("Analytics Setup Error", e.getMessage() + " ### Current Thread: " + Thread.currentThread().getId(), e);
            }
        });
    }

    @PluginMethod
    public void identify(PluginCall call) {
        Log.i("Load", "Identify");
        if (!AppsflyerPlugin.initialized) {
            call.reject(INITIALIZATION_ERROR_MESSAGE);
            return;
        }
        String userId = call.getString("userId");
        if (userId == null) {
            call.reject("User ID is required for 'identify' but not supplied");
            return;
        }
        Traits traits = this.getIdentificationTraits(call);
        AppsflyerPlugin.analytics.identify(userId, traits, null);
        call.resolve();
    }

    @PluginMethod
    public void track(PluginCall call) {
        Log.i("Load", "Track");
        if (!AppsflyerPlugin.initialized) {
            call.reject(INITIALIZATION_ERROR_MESSAGE);
            return;
        }
        String eventName = call.getString("eventName");
        Properties properties = this.getEventProperties(call);
        AppsflyerPlugin.analytics.track(eventName, properties);
        call.resolve();
    }

    @PluginMethod
    public void trackPage(PluginCall call) {
        Log.i("Load", "Track page");
        if (!AppsflyerPlugin.initialized) {
            call.reject(INITIALIZATION_ERROR_MESSAGE);
            return;
        }
        String eventName = call.getString("eventName");
        Properties properties = this.getEventProperties(call);
        AppsflyerPlugin.analytics.screen(eventName, properties);
        call.resolve();
    }

    private Traits getIdentificationTraits(PluginCall call) {
        JSObject eventValue = call.getObject("traits");
        Map<String, Object> propoertiesMap = makeMapFromJSON(eventValue);
        propoertiesMap.put(APPS_FLYER_UID_KEY, AppsflyerPlugin.appsFlyerUID);
        return makeTraitsFromMap(propoertiesMap);
    }

    private Properties getEventProperties(PluginCall call) {
        JSObject eventValue = call.getObject("properties");
        Map<String, Object> propoertiesMap = makeMapFromJSON(eventValue);
        propoertiesMap.put(APPS_FLYER_UID_KEY, AppsflyerPlugin.appsFlyerUID);
        return makePropertiesFromMap(propoertiesMap);
    }

    private Map<String, Object> makeMapFromJSON(JSObject obj) {
        Iterator<String> keys = obj.keys();
        Map<String, Object> map = new HashMap<>();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = obj.get(key);
                map.put(key, value);
            } catch (JSONException e) {
                Log.d(PLUGIN_TAG, "could not get value for key " + key);
            }
        }
        return map;
    }

    private Traits makeTraitsFromMap(Map<String, Object> map) {
        Traits traits = new Traits();
        traits.putAll(map);
        return traits;
    }

    private Properties makePropertiesFromMap(Map<String, Object> map) {
        Properties properties = new Properties();
        properties.putAll(map);
        return properties;
    }
}
