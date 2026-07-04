import React, { useRef, useEffect, useState } from "react";
import { StyleSheet, View, Text, Platform } from "react-native";
import { Camera } from "expo-camera";

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

// Dynamically resolve WebSocket URL from API backend base
const BASE_WS_URL = "wss://fitpilot-dips.onrender.com/api/v1";

const getWsUrl = (mode: "squat" | "curl") => {
  // If running locally, you can change this to "ws://localhost:8000/api/v1"
  const exercisePath = mode === "curl" ? "bicep" : "squat";
  return `${BASE_WS_URL}/workouts/ws/${exercisePath}`;
};

export const QuickPoseCameraWrapper: React.FC<QuickPoseCameraWrapperProps> = ({
  isActive,
  exerciseMode,
  onPoseUpdate,
  style,
}) => {
  const cameraRef = useRef<Camera>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const activeRef = useRef(isActive);
  const [connectionStatus, setConnectionStatus] = useState<"disconnected" | "connecting" | "connected">("disconnected");

  useEffect(() => {
    activeRef.current = isActive;
  }, [isActive]);

  // Handle WebSocket Connection and Camera Frame Streaming Loop
  useEffect(() => {
    if (!isActive) {
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      setConnectionStatus("disconnected");
      return;
    }

    setConnectionStatus("connecting");
    const wsUrl = getWsUrl(exerciseMode);
    console.log(`Connecting to WebSocket: ${wsUrl}`);
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    let frameTimer: NodeJS.Timeout | null = null;

    ws.onopen = () => {
      console.log("WebSocket connection established successfully");
      setConnectionStatus("connected");
      
      // Start the frame capture and transmission loop
      startFrameStreaming();
    };

    ws.onmessage = (event) => {
      try {
        const result = JSON.parse(event.data);
        if (result.error) {
          console.warn("Backend error returned:", result.error);
          return;
        }

        // Map backend response format to React Native state structure
        if (exerciseMode === "curl") {
          const reps = (result.left_reps || 0) + (result.right_reps || 0);
          const feedback: string[] = [];
          
          if (result.left_stage) feedback.push(`Left stage: ${result.left_stage}`);
          if (result.right_stage) feedback.push(`Right stage: ${result.right_stage}`);

          onPoseUpdate({
            reps,
            kneeAngle: "--",
            backAngle: "--",
            elbowAngle: `L: ${result.left_angle ?? "--"}° | R: ${result.right_angle ?? "--"}°`,
            feedback,
            isFormCorrect: true, // Server manages counts, form alerts can be customized
          });
        } else {
          // Squats
          const feedback: string[] = [];
          if (result.stage) feedback.push(`Stage: ${result.stage}`);
          
          onPoseUpdate({
            reps: result.reps || 0,
            kneeAngle: result.angle !== undefined ? `${result.angle}°` : "--",
            backAngle: "--",
            elbowAngle: "--",
            feedback,
            isFormCorrect: true,
          });
        }
      } catch (e) {
        console.error("Error parsing WebSocket response:", e);
      }
    };

    ws.onerror = (err) => {
      console.error("WebSocket error:", err);
      setConnectionStatus("disconnected");
    };

    ws.onclose = () => {
      console.log("WebSocket connection closed");
      setConnectionStatus("disconnected");
      if (frameTimer) clearTimeout(frameTimer);
    };

    const startFrameStreaming = async () => {
      if (!activeRef.current || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;

      try {
        if (cameraRef.current) {
          // Capture photo as compressed base64 to minimize network payload sizes
          const photo = await cameraRef.current.takePictureAsync({
            base64: true,
            quality: 0.15,
            skipProcessing: true,
          });

          if (photo?.base64 && wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
            wsRef.current.send(photo.base64);
          }
        }
      } catch (err) {
        console.warn("Frame capture failed:", err);
      }

      // Capture next frame in 100ms (~10 FPS target for real-time tracking)
      if (activeRef.current) {
        frameTimer = setTimeout(startFrameStreaming, 100);
      }
    };

    return () => {
      activeRef.current = false;
      if (frameTimer) clearTimeout(frameTimer);
      if (ws) {
        ws.close();
      }
    };
  }, [isActive, exerciseMode]);

  if (!isActive) {
    return (
      <View style={styles.cameraPlaceholder}>
        <Text style={styles.placeholderText}>Camera Feed Inactive</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Camera
        ref={cameraRef}
        style={style || styles.camera}
        type={Camera.Constants.Type.front}
      />
      <View style={styles.statusOverlay}>
        <View style={[styles.statusDot, connectionStatus === "connected" ? styles.statusDotConnected : connectionStatus === "connecting" ? styles.statusDotConnecting : null]} />
        <Text style={styles.statusText}>
          {connectionStatus === "connected" ? "Connected" : connectionStatus === "connecting" ? "Syncing..." : "Disconnected"}
        </Text>
      </View>
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
  statusOverlay: {
    position: "absolute",
    top: 70,
    alignSelf: "center",
    backgroundColor: "rgba(0, 0, 0, 0.6)",
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 20,
    flexDirection: "row",
    alignItems: "center",
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#ef4444",
    marginRight: 6,
  },
  statusDotConnected: {
    backgroundColor: "#10b981",
  },
  statusDotConnecting: {
    backgroundColor: "#f59e0b",
  },
  statusText: {
    color: "#FFFFFF",
    fontSize: 11,
    fontWeight: "600",
  },
});
