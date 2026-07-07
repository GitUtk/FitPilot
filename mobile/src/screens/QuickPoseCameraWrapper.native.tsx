import React from "react";
import { StyleSheet, View, Text, Platform } from "react-native";
import { ExpoPoseDetectorView, PoseUpdateData } from "../../modules/expo-pose-detector";

export { PoseUpdateData };

export interface QuickPoseCameraWrapperProps {
  isActive: boolean;
  exerciseMode: "squat" | "curl";
  onPoseUpdate: (data: PoseUpdateData) => void;
  style?: any;
}

export const QuickPoseCameraWrapper: React.FC<QuickPoseCameraWrapperProps> = ({
  isActive,
  exerciseMode,
  onPoseUpdate,
  style,
}) => {
  const handlePoseUpdate = (event: any) => {
    // Extract payload from the native event wrapper
    const data = event.nativeEvent;
    if (data) {
      onPoseUpdate(data);
    }
  };

  if (!isActive) {
    return (
      <View style={styles.cameraPlaceholder}>
        <Text style={styles.placeholderText}>Camera Feed Inactive</Text>
      </View>
    );
  }

  if (Platform.OS !== "android") {
    return (
      <View style={styles.cameraPlaceholder}>
        <Text style={styles.placeholderText}>Pose tracking is only supported on Android</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <ExpoPoseDetectorView
        isActive={isActive}
        exerciseMode={exerciseMode}
        onPoseUpdate={handlePoseUpdate}
        style={style || styles.camera}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    position: "relative",
  },
  camera: {
    flex: 1,
  },
  cameraPlaceholder: {
    flex: 1,
    backgroundColor: "#111111",
    justifyContent: "center",
    alignItems: "center",
  },
  placeholderText: {
    color: "rgba(255, 255, 255, 0.4)",
    fontSize: 14,
    fontWeight: "500",
  },
});
