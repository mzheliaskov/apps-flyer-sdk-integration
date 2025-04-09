import Foundation
import AppsFlyerLib
import Capacitor
import Segment

@objc(AppsflyerPlugin)
public class AppsflyerPlugin: CAPPlugin {
  private var APPS_FLYER_UID_KEY = "appsFlyerUID"
  private var initialized = false

  override public func load() {
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(self.handleUrlOpened(notification:)),
      name: Notification.Name.capacitorOpenURL,
      object: nil
    )
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(self.handleUniversalLink(notification:)),
      name: Notification.Name.capacitorOpenUniversalLink,
      object: nil
    )
    AppsFlyerLib.shared().deepLinkDelegate = self
  }

  @objc func initialize(_ call: CAPPluginCall) {
    let appsflyer = AppsFlyerLib.shared()
 
    guard let writeKey = call.getString("writeKey") else {
      call.reject("Segment write key not provided")
      return
    }

    let attInterval = call.getInt("waitForATTUserAuthorization")
    let debug = call.getBool("isDebug", false)
    let trackLifecycleEvents = call.getBool("trackLifecycleEvents", false)

    appsflyer.isDebug = debug

    reportBridgeReady()

    #if !AFSDK_NO_IDFA
      if attInterval != nil {
        appsflyer.waitForATTUserAuthorization(timeoutInterval: Double(attInterval!))
      }
    #endif

    let factoryWithDelegate: SEGAppsFlyerIntegrationFactory = SEGAppsFlyerIntegrationFactory.create(
      withLaunch: self, andDeepLinkDelegate: self)

    let config = AnalyticsConfiguration(writeKey: writeKey)
    config.use(factoryWithDelegate)
    config.enableAdvertisingTracking = true
    config.trackDeepLinks = true
    config.trackPushNotifications = true
    config.trackApplicationLifecycleEvents = trackLifecycleEvents

    Analytics.debug(debug)
    Analytics.setup(with: config)

    initialized = true
    call.resolve()
  }

  @objc func identify(_ call: CAPPluginCall) {
    if initialized != true {
      call.reject("Segment is not initialized")
      return
    }
    guard let userId = call.getString("userId") else {
      call.reject("User ID is not supplied")
      return
    }
    let traits: Dictionary = call.getObject("traits") ?? [:]
    Analytics.shared().identify(userId, traits: traits)
    call.resolve()
  }

  @objc func track(_ call: CAPPluginCall) {
    if initialized != true {
      call.reject("Segment is not initialized")
      return
    }
    guard let eventName = call.getString("eventName") else {
      call.reject("Event name is not supplied")
      return
    }
    var properties: Dictionary = call.getObject("properties") ?? [:]
    properties[APPS_FLYER_UID_KEY] = AppsFlyerLib.shared().getAppsFlyerUID();
    Analytics.shared().track(eventName, properties: properties)
    call.resolve()
  }

  @objc func trackPage(_ call: CAPPluginCall) {
    if initialized != true {
      call.reject("Segment is not initialized")
         return
     }
     guard let eventName = call.getString("eventName") else {
       call.reject("Event name is not supplied")
       return
     }
    var properties: Dictionary = call.getObject("properties") ?? [:]
    properties[APPS_FLYER_UID_KEY] = AppsFlyerLib.shared().getAppsFlyerUID();
    Analytics.shared().screen(eventName, properties: properties)
    call.resolve()
  }
}

extension AppsflyerPlugin {
  private func reportBridgeReady() {
    AppsFlyerAttribution.shared.bridgReady = true
    NotificationCenter.default.post(name: Notification.Name.appsflyerBridge, object: nil)
  }

  @objc func handleUrlOpened(notification: NSNotification) {
    guard let object = notification.object as? [String: Any?] else {
      return
    }
    guard let url = object["url"] else {
      afLogger(msg: "handleUrlOpened url is nil")
      return
    }
    guard let options = object["options"] else {
      afLogger(msg: "handleUrlOpened options is nil")
      return
    }
    afLogger(msg: "handleUrlOpened with \((url as! URL).absoluteString)")
    AppsFlyerAttribution.shared.handleOpenUrl(
      open: url as! URL, options: options as! [UIApplication.OpenURLOptionsKey: Any])
  }

  @objc func handleUniversalLink(notification: NSNotification) {
    guard let object = notification.object as? [String: Any?] else {
      return
    }
    let user = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
    guard let url = object["url"] else {
      afLogger(msg: "handleUrlOpened options is url")
      return
    }
    user.webpageURL = (url as! URL)
    afLogger(msg: "handleUniversalLink with \(user.webpageURL?.absoluteString ?? "null")")
    AppsFlyerAttribution.shared.continueUserActivity(userActivity: user)
  }
}

extension AppsflyerPlugin: SEGAppsFlyerLibDelegate {
  public func onConversionDataSuccess(_ conversionInfo: [AnyHashable: Any]) {
    let json: [String: Any] = ["callbackName": "onConversionDataSuccess", "data": conversionInfo]
    self.notifyListeners("conversion_callback", data: json)
  }

  public func onConversionDataFail(_ error: Error) {
    let json: [String: Any] = [
      "callbackName": "onConversionDataFail", "status": error.localizedDescription,
    ]
    self.notifyListeners("conversion_callback", data: json)
  }

  public func onAppOpenAttribution(_ attributionData: [AnyHashable: Any]) {
    let json: [String: Any] = ["callbackName": "onAppOpenAttribution", "data": attributionData]
    self.notifyListeners("oaoa_callback", data: json)
  }

  public func onAppOpenAttributionFailure(_ error: Error) {
    let json: [String: Any] = [
      "callbackName": "onAppOpenAttributionFailure", "status": error.localizedDescription,
    ]
    self.notifyListeners("oaoa_callback", data: json)
  }
}

extension AppsflyerPlugin: SEGAppsFlyerDeepLinkDelegate {
  public func didResolveDeepLink(_ result: DeepLinkResult) {
    var json: [String: Any] = [:]

    switch result.status {
    case .notFound:
      json["status"] = "NOT_FOUND"
    case .failure:
      json["status"] = "ERROR"
    case .found:
      json["status"] = "FOUND"
    }

    if result.error != nil {
      json["error"] = result.error!.localizedDescription
    }
    if result.deepLink != nil {
      var deepLinkDic = result.deepLink!.clickEvent
      deepLinkDic["is_deferred"] = result.deepLink!.isDeferred
      json["deepLink"] = deepLinkDic
    }
    self.notifyListeners("udl_callback", data: json)
  }
}

extension AppsflyerPlugin {
  private func afLogger(msg: String) {
    NSLog("AppsFlyer [Debug][Capacitor]: \(msg)")
  }
}