import { registerWebModule, NativeModule } from 'expo';

// ExpoPoseDetectorModule is not available on the web platform.
class ExpoPoseDetectorModule extends NativeModule<{}> {}

export default registerWebModule(ExpoPoseDetectorModule, 'ExpoPoseDetectorModule');
