import { bootstrapApplication } from '@angular/platform-browser';
import { createAppConfig } from './app/app.config';
import { App } from './app/app';
import { loadRuntimeConfig } from './app/core/config/runtime-config';

loadRuntimeConfig()
  .then((config) => bootstrapApplication(App, createAppConfig(config)))
  .catch((err) => console.error(err));
