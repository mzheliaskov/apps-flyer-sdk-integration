import { WebPlugin } from '@capacitor/core';

import type { AppsflyerPlugin } from './definitions';

export class AppsflyerWeb extends WebPlugin implements AppsflyerPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async identify(options: { userId: string; traits?: Record<string, unknown> }): Promise<void> {
    console.log('This method should be used only for native platforms', options);
  }

  async initialize(): Promise<void> {
    console.log('This method should be used only for native platforms');
  }

  async track(options: { eventName: string; properties: Record<string, unknown> }): Promise<void> {
    console.log('This method should be used only for native platforms', options);
  }

  async trackPage(options: { eventName: string; properties: Record<string, unknown> }): Promise<void> {
    console.log('This method should be used only for native platforms', options);
  }
}
