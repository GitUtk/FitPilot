import React, { useRef, useEffect } from "react";
import { StyleSheet, View, Text, Platform } from "react-native";
import ReactNativeMediapipePose, {
  ReactNativeMediapipePoseView,
  PoseDetectionResult,
} from "@gymbrosinc/react-native-mediapipe-pose";

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

// Helper to calculate 2D angle between three points: p1, p2 (vertex), p3
const calculateAngle = (
  p1: { x: number; y: number },
  p2: { x: number; y: number },
  p3: { x: number; y: number }
) => {
  const radians =
    Math.atan2(p3.y - p2.y, p3.x - p2.x) - Math.atan2(p1.y - p2.y, p1.x - p2.x);
  let angle = Math.abs((radians * 180.0) / Math.PI);
  if (angle > 180.0) {
    angle = 360.0 - angle;
  }
  return angle;
};

export const QuickPoseCameraWrapper: React.FC<QuickPoseCameraWrapperProps> = ({
  isActive,
  exerciseMode,
  onPoseUpdate,
  style,
}) => {
  const repsRef = useRef(0);
  const repStateRef = useRef<"up" | "down">("up");

  // Reset reps when exercise mode changes
  useEffect(() => {
    repsRef.current = 0;
    repStateRef.current = "up";
    onPoseUpdate({
      reps: 0,
      kneeAngle: "--",
      backAngle: "--",
      elbowAngle: "--",
      feedback: [],
      isFormCorrect: true,
    });
  }, [exerciseMode]);

  // Request camera permissions through package on iOS
  useEffect(() => {
    const requestPermission = async () => {
      try {
        if (Platform.OS === "ios") {
          await ReactNativeMediapipePose.requestCameraPermissions();
        }
      } catch (e) {
        console.log("Error requesting camera permissions via package:", e);
      }
    };
    if (isActive) {
      requestPermission();
    }
  }, [isActive]);

  const handlePoseDetected = (event: { nativeEvent: PoseDetectionResult }) => {
    const { landmarks } = event.nativeEvent;
    if (!landmarks || landmarks.length === 0) return;

    // Determine the side of the body with better visibility/confidence
    // Left landmarks: shoulder (11), elbow (13), wrist (15), hip (23), knee (25), ankle (27)
    // Right landmarks: shoulder (12), elbow (14), wrist (16), hip (24), knee (26), ankle (28)
    const leftArmVis =
      (landmarks[11]?.visibility || 0) +
      (landmarks[13]?.visibility || 0) +
      (landmarks[15]?.visibility || 0);
    const rightArmVis =
      (landmarks[12]?.visibility || 0) +
      (landmarks[14]?.visibility || 0) +
      (landmarks[16]?.visibility || 0);
    const armSide = leftArmVis >= rightArmVis ? "left" : "right";

    const shoulder = landmarks[armSide === "left" ? 11 : 12];
    const elbow = landmarks[armSide === "left" ? 13 : 14];
    const wrist = landmarks[armSide === "left" ? 15 : 16];
    const hip = landmarks[armSide === "left" ? 23 : 24];

    const leftLegVis =
      (landmarks[23]?.visibility || 0) +
      (landmarks[25]?.visibility || 0) +
      (landmarks[27]?.visibility || 0);
    const rightLegVis =
      (landmarks[24]?.visibility || 0) +
      (landmarks[26]?.visibility || 0) +
      (landmarks[28]?.visibility || 0);
    const legSide = leftLegVis >= rightLegVis ? "left" : "right";

    const sHip = landmarks[legSide === "left" ? 23 : 24];
    const sKnee = landmarks[legSide === "left" ? 25 : 26];
    const sAnkle = landmarks[legSide === "left" ? 27 : 28];
    const sShoulder = landmarks[legSide === "left" ? 11 : 12];

    let kneeAngle: number | string = "--";
    let backAngle: number | string = "--";
    let elbowAngle: number | string = "--";
    let feedback: string[] = [];
    let isFormCorrect = true;

    // 1. Calculate angles if landmarks are visible (confidence > 0.5)
    if (
      shoulder &&
      elbow &&
      wrist &&
      shoulder.visibility > 0.5 &&
      elbow.visibility > 0.5 &&
      wrist.visibility > 0.5
    ) {
      elbowAngle = Math.round(calculateAngle(shoulder, elbow, wrist));
    }

    if (
      sHip &&
      sKnee &&
      sAnkle &&
      sHip.visibility > 0.5 &&
      sKnee.visibility > 0.5 &&
      sAnkle.visibility > 0.5
    ) {
      kneeAngle = Math.round(calculateAngle(sHip, sKnee, sAnkle));
    }

    const chosenHip = exerciseMode === "squat" ? sHip : hip;
    const chosenShoulder = exerciseMode === "squat" ? sShoulder : shoulder;
    if (
      chosenHip &&
      chosenShoulder &&
      chosenHip.visibility > 0.5 &&
      chosenShoulder.visibility > 0.5
    ) {
      // Calculate back lean relative to vertical line
      const dx = chosenShoulder.x - chosenHip.x;
      const dy = chosenShoulder.y - chosenHip.y;
      const rad = Math.atan2(Math.abs(dx), Math.abs(dy));
      const angleFromVertical = (rad * 180.0) / Math.PI;
      backAngle = Math.round(180 - angleFromVertical);
    }

    // 2. State-Machine Rep Counter and Real-time Coaching Feedback
    if (exerciseMode === "curl") {
      if (typeof elbowAngle === "number") {
        if (elbowAngle < 60) {
          feedback.push("Good squeeze at top!");
          if (repStateRef.current === "up") {
            repStateRef.current = "down";
          }
        } else {
          feedback.push("Curl: Lift weights upward.");
          if (repStateRef.current === "down" && elbowAngle > 130) {
            repStateRef.current = "up";
            repsRef.current += 1;
          }
        }
      }

      if (typeof backAngle === "number" && backAngle < 155) {
        feedback.push("Avoid leaning back.");
        isFormCorrect = false;
      }

      if (shoulder && elbow && hip) {
        const shoulderToHipX = Math.abs(shoulder.x - hip.x);
        const elbowToHipX = Math.abs(elbow.x - hip.x);
        if (elbowToHipX > shoulderToHipX * 1.5) {
          feedback.push("Keep elbows tucked to your side.");
          isFormCorrect = false;
        }
      }
    } else if (exerciseMode === "squat") {
      if (typeof kneeAngle === "number") {
        if (kneeAngle < 110) {
          feedback.push("Good depth!");
          if (repStateRef.current === "up") {
            repStateRef.current = "down";
          }
        } else {
          feedback.push("Squat: Lower your hips.");
          if (repStateRef.current === "down" && kneeAngle > 150) {
            repStateRef.current = "up";
            repsRef.current += 1;
          }
        }
      }

      if (typeof backAngle === "number" && backAngle < 145) {
        feedback.push("Keep your back straight.");
        isFormCorrect = false;
      }

      if (landmarks[25] && landmarks[26] && landmarks[23] && landmarks[24]) {
        const kneeDist = Math.abs(landmarks[25].x - landmarks[26].x);
        const hipDist = Math.abs(landmarks[23].x - landmarks[24].x);
        if (kneeDist < hipDist * 0.8) {
          feedback.push("Knees caving in — push them out.");
          isFormCorrect = false;
        }
      }
    }

    onPoseUpdate({
      reps: repsRef.current,
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
    <ReactNativeMediapipePoseView
      style={style || styles.camera}
      cameraType="front"
      enablePoseDetection={true}
      enablePoseDataStreaming={true}
      targetFPS={30}
      autoAdjustFPS={true}
      onPoseDetected={handlePoseDetected}
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
