import React, { useState, useEffect, useRef } from "react";
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  Platform,
  SafeAreaView,
  ActivityIndicator,
  StatusBar,
} from "react-native";
import { Camera } from "expo-camera";
import { QuickPoseCameraWrapper, PoseUpdateData } from "./QuickPoseCameraWrapper";
import { COLORS, SPACING, SIZES } from "../styles/theme";

type ExerciseMode = "squat" | "curl";

export const PoseScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [isActive, setIsActive] = useState(false);
  const [exerciseMode, setExerciseMode] = useState<ExerciseMode>("squat");
  const [kneeAngle, setKneeAngle] = useState<number | string>("--");
  const [backAngle, setBackAngle] = useState<number | string>("--");
  const [elbowAngle, setElbowAngle] = useState<number | string>("--");
  const [feedback, setFeedback] = useState<string[]>([]);
  const [isFormCorrect, setIsFormCorrect] = useState(true);
  const [reps, setReps] = useState<number>(0);

  const activeRef = useRef(false);

  // Reset stats when switching exercises
  useEffect(() => {
    setReps(0);
    setKneeAngle("--");
    setBackAngle("--");
    setElbowAngle("--");
    setFeedback([]);
    setIsFormCorrect(true);
  }, [exerciseMode]);

  useEffect(() => {
    const getPermissions = async () => {
      if (Platform.OS !== "web") {
        try {
          const { status: cameraStatus } = await Camera.requestCameraPermissionsAsync();
          setHasPermission(cameraStatus === "granted");
        } catch (e) {
          console.log("Error requesting camera permissions:", e);
          setHasPermission(false);
        }
      } else {
        setHasPermission(true);
      }
    };
    getPermissions();

    return () => {
      stopSession();
    };
  }, []);

  const handlePoseUpdate = (data: PoseUpdateData) => {
    setReps(data.reps);
    setKneeAngle(data.kneeAngle);
    setBackAngle(data.backAngle);
    setElbowAngle(data.elbowAngle);
    setFeedback(data.feedback);
    setIsFormCorrect(data.isFormCorrect);
  };

  const startSession = async () => {
    setIsActive(true);
    activeRef.current = true;
    setReps(0);
  };

  const stopSession = () => {
    setIsActive(false);
    activeRef.current = false;
    setReps(0);
    setKneeAngle("--");
    setBackAngle("--");
    setElbowAngle("--");
    setFeedback([]);
    setIsFormCorrect(true);
  };

  if (hasPermission === null) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#FFFFFF" />
        <Text style={styles.loadingText}>Requesting camera permission...</Text>
      </View>
    );
  }

  if (hasPermission === false) {
    return (
      <View style={styles.loadingContainer}>
        <Text style={styles.errorText}>No access to camera. Please enable camera permissions in settings.</Text>
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.cameraViewport}>
        <QuickPoseCameraWrapper
          isActive={isActive}
          exerciseMode={exerciseMode}
          onPoseUpdate={handlePoseUpdate}
          style={styles.fullscreenNativeCamera}
        />

        <View style={styles.headerOverlay}>
          <TouchableOpacity onPress={() => { stopSession(); navigation.navigate("Workouts"); }} style={styles.backCircle}>
            <Text style={styles.backArrow}>←</Text>
          </TouchableOpacity>
          <View style={styles.modeToggleContainer}>
            <TouchableOpacity
              style={[styles.modeTab, exerciseMode === "squat" && styles.modeTabActive]}
              onPress={() => setExerciseMode("squat")}
            >
              <Text style={[styles.modeTabText, exerciseMode === "squat" && styles.modeTabTextActive]}>
                Squat
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.modeTab, exerciseMode === "curl" && styles.modeTabActive]}
              onPress={() => setExerciseMode("curl")}
            >
              <Text style={[styles.modeTabText, exerciseMode === "curl" && styles.modeTabTextActive]}>
                Curl
              </Text>
            </TouchableOpacity>
          </View>
          <View style={styles.invisiblePlaceholder} />
        </View>

        <View style={styles.hudOverlay}>
          <View style={styles.statsPanel}>
            <View style={styles.statPill}>
              <Text style={styles.statLabel}>KNEE</Text>
              <Text style={styles.statVal}>{kneeAngle}°</Text>
            </View>
            <View style={styles.statPill}>
              <Text style={styles.statLabel}>BACK</Text>
              <Text style={styles.statVal}>{backAngle}°</Text>
            </View>
            <View style={styles.statPill}>
              <Text style={styles.statLabel}>ELBOW</Text>
              <Text style={styles.statVal}>{elbowAngle}°</Text>
            </View>
            <View style={[styles.statPill, { backgroundColor: "rgba(16, 185, 129, 0.12)", borderColor: "rgba(16, 185, 129, 0.35)" }]}>
              <Text style={[styles.statLabel, { color: "#10B981" }]}>REPS</Text>
              <Text style={[styles.statVal, { color: "#10B981", fontWeight: "700" }]}>{reps}</Text>
            </View>
          </View>

          <View style={[styles.feedbackPanel, !isFormCorrect && styles.feedbackPanelError]}>
            <Text style={styles.panelTitle}>Biomechanical Analysis</Text>
            {feedback.length === 0 ? (
              <Text style={styles.noFeedback}>Press start to run AI evaluation</Text>
            ) : (
              feedback.map((item, index) => (
                <Text key={index} style={[styles.feedbackLine, !isFormCorrect && styles.feedbackLineError]}>
                  {item}
                </Text>
              ))
            )}
          </View>

          <TouchableOpacity
            style={[styles.sessionButton, isActive ? styles.stopButton : styles.startButton]}
            onPress={isActive ? stopSession : startSession}
          >
            <Text style={[styles.sessionButtonText, isActive && { color: "#FFFFFF" }]}>
              {isActive ? "Stop Scanner" : "Start Real-Time Check"}
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#000000",
  },
  loadingContainer: {
    flex: 1,
    backgroundColor: "#000000",
    justifyContent: "center",
    alignItems: "center",
  },
  loadingText: {
    color: "#FFFFFF",
    marginTop: 12,
    fontSize: 14,
    fontWeight: "500",
  },
  errorText: {
    color: "#EF4444",
    fontSize: 14,
  },
  cameraViewport: {
    flex: 1,
    position: "relative",
    width: "100%",
    height: "100%",
  },
  fullscreenWebCamera: {
    position: "absolute",
    top: 0,
    left: 0,
    width: "100%",
    height: "100%",
    backgroundColor: "#000000",
  },
  webVideoElement: {
    width: "100%",
    height: "100%",
    objectFit: "cover",
  },
  webCanvasOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    width: "100%",
    height: "100%",
  },
  fullscreenNativeCameraContainer: {
    position: "absolute",
    top: 0,
    left: 0,
    width: "100%",
    height: "100%",
  },
  fullscreenNativeCamera: {
    flex: 1,
  },
  headerOverlay: {
    position: "absolute",
    top: Platform.OS === "ios" ? 10 : (StatusBar.currentHeight || 24) + 10,
    left: 0,
    right: 0,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: SPACING.md,
    zIndex: 10,
  },
  backCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "rgba(0, 0, 0, 0.4)",
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.1)",
  },
  backArrow: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "bold",
  },
  modeToggleContainer: {
    flexDirection: "row",
    backgroundColor: "rgba(0, 0, 0, 0.5)",
    borderRadius: 20,
    padding: 2,
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.1)",
  },
  modeTab: {
    paddingVertical: 4,
    paddingHorizontal: 16,
    borderRadius: 18,
  },
  modeTabActive: {
    backgroundColor: "#FFFFFF",
  },
  modeTabText: {
    color: "rgba(255, 255, 255, 0.8)",
    fontSize: 12,
    fontWeight: "600",
  },
  modeTabTextActive: {
    color: "#000000",
  },
  invisiblePlaceholder: {
    width: 36,
  },
  loadingOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(15, 23, 42, 0.85)",
    zIndex: 12,
  },
  spinner: {
    marginBottom: SPACING.md,
  },
  loadingOverlayText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "500",
    textAlign: "center",
    lineHeight: 20,
  },
  simulatedIndicator: {
    position: "absolute",
    top: 70,
    alignSelf: "center",
    backgroundColor: "rgba(16, 185, 129, 0.8)",
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 12,
    zIndex: 10,
  },
  simulatedText: {
    color: "#FFFFFF",
    fontSize: 11,
    fontWeight: "600",
  },
  hudOverlay: {
    position: "absolute",
    bottom: SPACING.lg,
    left: SPACING.md,
    right: SPACING.md,
    zIndex: 10,
  },
  statsPanel: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: SPACING.md,
  },
  statPill: {
    flex: 1,
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    borderRadius: SIZES.radiusSm,
    paddingVertical: SPACING.sm,
    marginHorizontal: 4,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.18)",
    shadowColor: "#000000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 2,
  },
  statLabel: {
    fontSize: 9,
    fontWeight: "600",
    color: "rgba(255, 255, 255, 0.5)",
    letterSpacing: 0.5,
    marginBottom: 2,
  },
  statVal: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#FFFFFF",
  },
  feedbackPanel: {
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    borderRadius: SIZES.radiusSm,
    padding: SPACING.md,
    marginBottom: SPACING.md,
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.18)",
    minHeight: 70,
    shadowColor: "#000000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 2,
  },
  feedbackPanelError: {
    backgroundColor: "rgba(239, 68, 68, 0.12)",
    borderColor: "rgba(239, 68, 68, 0.35)",
  },
  panelTitle: {
    fontSize: 11,
    fontWeight: "600",
    color: "rgba(255, 255, 255, 0.4)",
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginBottom: 6,
  },
  noFeedback: {
    fontSize: 13,
    color: "rgba(255, 255, 255, 0.6)",
    fontStyle: "italic",
  },
  feedbackLine: {
    fontSize: 13,
    color: "#FFFFFF",
    fontWeight: "500",
    marginBottom: 3,
  },
  feedbackLineError: {
    color: "#FCA5A5",
  },
  sessionButton: {
    height: SIZES.inputHeight,
    borderRadius: SIZES.radiusSm,
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#000000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 4,
  },
  startButton: {
    backgroundColor: "rgba(255, 255, 255, 0.15)",
    borderWidth: 1.5,
    borderColor: "rgba(255, 255, 255, 0.35)",
  },
  stopButton: {
    backgroundColor: "rgba(239, 68, 68, 0.35)",
    borderWidth: 1.5,
    borderColor: "rgba(239, 68, 68, 0.55)",
  },
  sessionButtonText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "700",
    letterSpacing: 0.5,
  },
  webFallbackText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "500",
    textAlign: "center",
    padding: SPACING.md,
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
