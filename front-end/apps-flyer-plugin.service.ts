import { inject, Injectable } from '@angular/core';
import { from, Observable, Subject, tap } from 'rxjs';
import {
  APPS_FLYER_SDK_PLUGIN_CONFIG,
  Appsflyer,
  AppsflyerPlugin,
  AppsFlyerSdkPluginConfig,
  ConversionData,
  ConversionDataMapper,
  DeepLinkEventPayload,
  DeepLinkStatus,
  EventNames,
  NoopAppsflyerPlugin,
  OnConversionDataResult,
  OnDeepLink
} from 'source';

@Injectable({ providedIn: 'root' })
export class AppsFlyerSdkPluginService {
  private af: AppsflyerPlugin = new NoopAppsflyerPlugin();
  private readonly config: AppsFlyerSdkPluginConfig = inject(APPS_FLYER_SDK_PLUGIN_CONFIG);
  private readonly conversionDataMapper: ConversionDataMapper = inject(ConversionDataMapper);

  public readonly onDeepLink$: Subject<DeepLinkEventPayload> = new Subject<DeepLinkEventPayload>();

  initialize$(): Observable<void> {
    this.setupEventListeners();
    return from(
      Appsflyer.initialize({
        writeKey: this.config.writeKey,
        isDebug: this.config.isDebug,
        waitForATTUserAuthorization: this.config.waitForATTUserAuthorization,
        trackLifecycleEvents: this.config.trackLifecycleEvents
      })
    ).pipe(tap(() => this.af = Appsflyer));
  }

  private setupEventListeners(): void {
    this.setUpDeepLinkEventHandler();
    this.setUpConversionEventHandler();
  }

  private setUpDeepLinkEventHandler(): void {
    Appsflyer.addListener(EventNames.ON_DEEP_LINK_CALLBACK, (event: OnDeepLink) => {
      this.onDeepLink$.next(event);
    });
  }

  private setUpConversionEventHandler(): void {
    Appsflyer.addListener(EventNames.CONVERSION_CALLBACK, (event: OnConversionDataResult) => {
      const data: ConversionData = event.data;
      if (data?.is_first_launch && data?.shortlink) {
        this.onDeepLink$.next({
          status: DeepLinkStatus.FOUND,
          deepLink: this.conversionDataMapper.toDeepLink(data)
        });
      }
    });
  }
}