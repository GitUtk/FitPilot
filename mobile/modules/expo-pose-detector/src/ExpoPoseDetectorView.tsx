import { requireNativeView } from "expo";
import * as React from "react";
import { ViewProps, Platform, View, Text } from "react-native";

export interface PoseUpdateData {
  reps: number;
  kneeAngle: number | string;
  backAngle: number | string;
  elbowAngle: number | string;
  feedback: string[];
  isFormCorrect: boolean;
}

export interface ExpoPoseDetectorViewProps extends ViewProps {
  exerciseMode: "squat" | "curl";
  isActive: boolean;
  onPoseUpdate?: (event: { nativeEvent: PoseUpdateData }) => void;
}

const NativeView: React.ComponentType<ExpoPoseDetectorViewProps> | null =
  Platform.OS === "android" ? requireNativeView("ExpoPoseDetector") : null;

export default function ExpoPoseDetectorView(props: ExpoPoseDetectorViewProps) {
  if (Platform.OS !== "android" || !NativeView) {
    return (
      <View style={[{ flex: 1, backgroundColor: "#111", justifyContent: "center", alignItems: "center" }, props.style]}>
        <Text style={{ color: "#fff", textAlign: "center", padding: 20 }}>
          Pose detection is only supported on Android devices.
        </Text>
      </View>
    );
  }
  return <NativeView {...props} />;
}
