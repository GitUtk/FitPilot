import React from "react";
import { View, Text, StyleSheet } from "react-native";

export interface PoseUpdateData {
  reps: number;
  kneeAngle: number | string;
  backAngle: number | string;
  elbowAngle: number | string;
  feedback: string[];
  isFormCorrect: boolean;
}

export interface QuickPoseCameraWrapperProps {
  isActive: boolean;
  exerciseMode: "squat" | "curl";
  onPoseUpdate: (data: PoseUpdateData) => void;
  style?: any;
}

export const QuickPoseCameraWrapper: React.FC<QuickPoseCameraWrapperProps> = () => {
  return (
    <View style={styles.fallbackContainer}>
      <Text style={styles.fallbackText}>
        Pose Tracking is supported on iOS and Android devices.
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  fallbackContainer: {
    flex: 1,
    backgroundColor: "#111111",
    justifyContent: "center",
    alignItems: "center",
    padding: 20,
  },
  fallbackText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "500",
    textAlign: "center",
  },
});
