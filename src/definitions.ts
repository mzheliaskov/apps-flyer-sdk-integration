import { PluginListenerHandle } from "@capacitor/core";

export enum EventNames {
  CONVERSION_CALLBACK = 'conversion_callback',
  ON_APP_OPEN_ATTRIBUTION_CALLBACK = 'oaoa_callback',
  ON_DEEP_LINK_CALLBACK = 'udl_callback'
}

export interface AppsflyerPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;

  addListener(
    eventName: EventNames.CONVERSION_CALLBACK,
    listenerFunc: (event: OnConversionDataResult) => void
  ): PluginListenerHandle;

  addListener(
    eventName: EventNames.ON_APP_OPEN_ATTRIBUTION_CALLBACK,
    listenerFunc: (event: OnAppOpenAttribution) => void
  ): PluginListenerHandle;

  addListener(
    eventName: EventNames.ON_DEEP_LINK_CALLBACK,
    listenerFunc: (event: OnDeepLink) => void
  ): PluginListenerHandle;

  initialize(options: {
    writeKey: string;
    isDebug?: boolean;
    waitForATTUserAuthorization?: number;
    trackLifecycleEvents?: boolean;
  }): Promise<void>;

  identify(options: {userId: string; traits?: Record<string, unknown>}): Promise<void>;

  track(options: {eventName: string; properties: Record<string, unknown>}): Promise<void>;

  trackPage(options: {eventName: string; properties: Record<string, unknown>}): Promise<void>;
}

export interface OnConversionDataResult {
  callbackName: string;
  errorMessage?: string;
  data?: any;
}

export interface OnAppOpenAttribution {
  callbackName: string;
  errorMessage?: string;
  data?: any;
}

export interface OnDeepLink {
  status: string;
  error?: string;
  deepLink?: any;
}
