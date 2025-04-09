package com.nursa.appsflyer;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.appsflyer.AFInAppEventParameterName;
import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.appsflyer.deeplink.DeepLinkListener;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.segment.analytics.internal.Utils.transform;

/**
 * Created by shacharaharon on 12/04/2016.
 */
public class AppsflyerIntegration extends Integration<AppsFlyerLib> {
    private static final String APPSFLYER_KEY = "AppsFlyer";
    private static final String SEGMENT_REVENUE = "revenue";
    private static final String SEGMENT_CURRENCY = "currency";
    private Context context;
    private String customerUserId;
    private String currencyCode;

    public static final String AF_SEGMENT_SHARED_PREF = "appsflyer-segment-data";
    public static final String CONV_KEY = "AF_onConversion_Data";
    public static final Map<String, String> MAPPER;
    public static AppsFlyerConversionListener conversionListener;
    public static DeepLinkListener deepLinkListener;
    public static ConversionListenerDisplay cld;
    public final Logger logger;
    public final AppsFlyerLib appsflyer;
    public final String appsFlyerDevKey;
    public final boolean isDebug;

    /**
     * Responsible to map revenue -> af_revenue , currency -> af_currency
     */
    static {
        Map<String, String> mapper = new LinkedHashMap<>();
        mapper.put(SEGMENT_REVENUE, AFInAppEventParameterName.REVENUE);
        mapper.put(SEGMENT_CURRENCY, AFInAppEventParameterName.CURRENCY);
        MAPPER = Collections.unmodifiableMap(mapper);
    }

    public static final Factory FACTORY = new Integration.Factory() {
        @Override
        public Integration<AppsFlyerLib> create(ValueMap settings, Analytics analytics) {
            Logger analyticsLogger = analytics.logger(APPSFLYER_KEY);
            AppsFlyerLib afLib = AppsFlyerLib.getInstance();

            String devKey = settings.getString("appsFlyerDevKey");
            boolean trackAttributionData = settings.getBoolean("trackAttributionData", false);
            Application application = analytics.getApplication();

            AppsFlyerConversionListener listener = null;
            if (trackAttributionData) {
                listener = new ConversionListener(analytics);
            }
            afLib.setDebugLog(analyticsLogger.logLevel != Analytics.LogLevel.NONE);
            analyticsLogger.debug("AppsFlyerLib.getInstance().init(%s, %s)", application, devKey.substring(0, 1) + "*****" + devKey.substring(devKey.length() - 2));
            afLib.init(devKey, listener, application.getApplicationContext());

            if (deepLinkListener != null) {
                AppsFlyerLib.getInstance().subscribeForDeepLink(deepLinkListener);
            }

            return new AppsflyerIntegration(application, analyticsLogger, afLib, devKey);
        }

        @Override
        public String key() {
            return APPSFLYER_KEY;
        }
    };

    public AppsflyerIntegration(Context context, Logger logger, AppsFlyerLib afLib, String devKey) {
        this.context = context;
        this.logger = logger;
        this.appsflyer = afLib;
        this.appsFlyerDevKey = devKey;
        this.isDebug = (logger.logLevel != Analytics.LogLevel.NONE);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        super.onActivityCreated(activity, savedInstanceState);
        updateEndUserAttributes();
    }

    @Override
    public AppsFlyerLib getUnderlyingInstance() {
        return appsflyer;
    }

    @Override
    public void identify(IdentifyPayload identify) {
        super.identify(identify);

        customerUserId = identify.userId();
        currencyCode = identify.traits().getString("currencyCode");

        if (appsflyer != null) {
            updateEndUserAttributes();
        } else {
            logger.debug("couldn't update 'Identify' attributes");
        }
    }

    @Override
    public void track(TrackPayload track) {
        String event = track.event();
        Properties properties = track.properties();
        Map<String, Object> afProperties = transform(properties, MAPPER);
        appsflyer.logEvent(context, event, afProperties);
        logger.debug("appsflyer.logEvent(context, %s, %s)", event, properties);
    }

    public interface ConversionListenerDisplay {
        void display(Map<String, ?> attributionData);
    }

    static class ConversionListener implements AppsFlyerConversionListener {
        final Analytics analytics;

        public ConversionListener(Analytics analytics) {
            this.analytics = analytics;
        }

        @Override
        public void onConversionDataSuccess(Map<String, Object> conversionData) {
            if (!getFlag(CONV_KEY)) {
                trackInstallAttributed(conversionData);
                setFlag(CONV_KEY, true);
            }
            if (cld != null) {
                conversionData.put("type", "onInstallConversionData");
                cld.display(conversionData);
            }
            if (conversionListener != null) {
                conversionListener.onConversionDataSuccess(conversionData);
            }
        }

        @Override
        public void onConversionDataFail(String errorMessage) {
            if (conversionListener != null) {
                conversionListener.onConversionDataFail(errorMessage);
            }
        }

        @Override
        public void onAppOpenAttribution(Map<String, String> attributionData) {
            if (cld != null) {
                attributionData.put("type", "onAppOpenAttribution");
                cld.display(attributionData);
            }
            if (conversionListener != null) {
                conversionListener.onAppOpenAttribution(attributionData);
            }
        }

        @Override
        public void onAttributionFailure(String errorMessage) {
            if (conversionListener != null) {
                conversionListener.onAttributionFailure(errorMessage);
            }
        }

        private Object getFromAttr(Object value) {
            return (value != null) ? value : "";
        }

        void trackInstallAttributed(Map<String, ?> attributionData) {
            // See https://segment.com/docs/spec/mobile/#install-attributed.
            Map<String, Object> campaign = new ValueMap() //
                    .putValue("source", getFromAttr(attributionData.get("media_source")))
                    .putValue("name", getFromAttr(attributionData.get("campaign")))
                    .putValue("ad_group", getFromAttr(attributionData.get("adgroup")));

            Properties properties = new Properties().putValue("provider", APPSFLYER_KEY);
            properties.putAll(attributionData);

            // Remove properties set in campaign.
            properties.remove("media_source");
            properties.remove("adgroup");

            // replace original campaign with new created
            properties.putValue("campaign", campaign);

            // If you are working with networks that don't allow passing user level data to 3rd parties,
            // you will need to apply code to filter out these networks before calling
            // `analytics.track("Install Attributed", properties);`
            analytics.track("Install Attributed", properties);
        }

        private boolean getFlag(final String key) {
            Context context = getContext();
            if (context == null) {
                return false;
            }
            SharedPreferences sharedPreferences = context.getSharedPreferences(AF_SEGMENT_SHARED_PREF, 0);
            return sharedPreferences.getBoolean(key, false);
        }

        private void setFlag(final String key, final boolean value) {
            Context context = getContext();
            if (context == null) {
                return;
            }
            SharedPreferences sharedPreferences = context.getSharedPreferences(AF_SEGMENT_SHARED_PREF, 0);
            android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(key, value);
            editorCommit(editor);
        }

        private void editorCommit(SharedPreferences.Editor editor) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
                editor.apply();
            } else {
                editor.commit();
            }
        }

        private Context getContext() {
            return this.analytics.getApplication().getApplicationContext();
        }
    }

    private void updateEndUserAttributes() {
        appsflyer.setCustomerUserId(customerUserId);
        logger.debug("appsflyer.setCustomerUserId(%s)", customerUserId);
        appsflyer.setCurrencyCode(currencyCode);
        logger.debug("appsflyer.setCurrencyCode(%s)", currencyCode);
        appsflyer.setDebugLog(isDebug);
        logger.debug("appsflyer.setDebugLog(%s)", isDebug);
    }
}
