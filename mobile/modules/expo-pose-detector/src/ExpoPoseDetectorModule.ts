import { NativeModule, requireNativeModule } from 'expo';

declare class ExpoPoseDetectorModule extends NativeModule<{}> {}

export default requireNativeModule<ExpoPoseDetectorModule>('ExpoPoseDetector');
