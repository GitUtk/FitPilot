// Re-export the native module. On web, it will be resolved to ExpoPoseDetectorModule.web.ts
// and on native platforms to ExpoPoseDetectorModule.ts
export { default } from './src/ExpoPoseDetectorModule';
export { default as ExpoPoseDetectorView } from './src/ExpoPoseDetectorView';
export * from './src/ExpoPoseDetectorView';
export * from './src/ExpoPoseDetector.types';
