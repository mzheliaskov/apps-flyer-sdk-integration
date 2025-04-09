import { registerPlugin } from '@capacitor/core';

import type { AppsflyerPlugin } from './definitions';

const Appsflyer = registerPlugin<AppsflyerPlugin>('Appsflyer', {
  web: () => import('./web').then(m => new m.AppsflyerWeb()),
});

export * from './definitions';
export { Appsflyer };
