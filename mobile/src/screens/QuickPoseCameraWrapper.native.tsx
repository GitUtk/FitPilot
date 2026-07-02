import React, { useMemo, useRef, useEffect } from "react";
import { StyleSheet, View, Text } from "react-native";
import { QuickPoseView, QuickPoseThresholdCounter } from "@quickpose/react-native";

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

export const QuickPoseCameraWrapper: React.FC<QuickPoseCameraWrapperProps> = ({
  isActive,
  exerciseMode,
  onPoseUpdate,
  style,
}) => {
  const thresholdCounterRef = useRef(new QuickPoseThresholdCounter(0.6, 0.3));

  // Reset reps count when exercise mode changes
  useEffect(() => {
    thresholdCounterRef.current.reset();
    onPoseUpdate({
      reps: 0,
      kneeAngle: "--",
      backAngle: "--",
      elbowAngle: "--",
      feedback: [],
      isFormCorrect: true,
    });
  }, [exerciseMode]);

  const activeFeatures = useMemo(() => {
    const feat = exerciseMode === "squat" ? "fitness.squats" : "fitness.bicepCurls";
    return [feat, "overlay.wholeBody", "rangeOfMotion.knee", "rangeOfMotion.elbow", "rangeOfMotion.back"];
  }, [exerciseMode]);

  const handleQuickPoseUpdate = (event: any) => {
    const { results, feedbacks } = event.nativeEvent;
    if (!results) return;

    let kneeAngle: number | string = "--";
    let backAngle: number | string = "--";
    let elbowAngle: number | string = "--";
    let reps = 0;
    let feedback: string[] = [];
    let isFormCorrect = true;

    // 1. Extract rangeOfMotion angles
    const kneeAngleVal = results["rangeOfMotion.knee"];
    if (kneeAngleVal !== undefined) {
      kneeAngle = Math.round(kneeAngleVal);
    }

    const backAngleVal = results["rangeOfMotion.back"];
    if (backAngleVal !== undefined) {
      backAngle = Math.round(backAngleVal);
    }

    const elbowAngleVal = results["rangeOfMotion.elbow"];
    if (elbowAngleVal !== undefined) {
      elbowAngle = Math.round(elbowAngleVal);
    }

    // 2. Count reps using the threshold counter
    const activeFeat = exerciseMode === "squat" ? "fitness.squats" : "fitness.bicepCurls";
    const score = results[activeFeat];
    if (score !== undefined) {
      const counterState = thresholdCounterRef.current.count(score);
      reps = counterState.count;
    }

    // 3. Extract coaching feedback alerts
    const activeFeedback = feedbacks[activeFeat];
    if (activeFeedback) {
      feedback = [activeFeedback];
      isFormCorrect = !activeFeedback.toLowerCase().includes("avoid") &&
                      !activeFeedback.toLowerCase().includes("keep") &&
                      !activeFeedback.toLowerCase().includes("lower") &&
                      !activeFeedback.toLowerCase().includes("lean");
    }

    onPoseUpdate({
      reps,
      kneeAngle,
      backAngle,
      elbowAngle,
      feedback,
      isFormCorrect,
    });
  };

  if (!isActive) {
    return (
      <View style={styles.cameraPlaceholder}>
        <Text style={styles.placeholderText}>Camera Feed Inactive</Text>
      </View>
    );
  }

  return (
    <QuickPoseView
      sdkKey="free-trial"
      features={activeFeatures}
      useFrontCamera={true}
      style={style || styles.camera}
      onUpdate={handleQuickPoseUpdate}
    />
  );
};

const styles = StyleSheet.create({
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
