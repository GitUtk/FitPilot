import React, { useState, useEffect, useRef } from "react";
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  Platform,
  SafeAreaView,
  ActivityIndicator,
} from "react-native";
import { Camera, CameraView } from "expo-camera";
import { COLORS, SPACING, SIZES } from "../styles/theme";

type ExerciseMode = "squat" | "curl";

const SkeletonOverlay: React.FC<{ points: any; isFormCorrect: boolean }> = ({ points, isFormCorrect }) => {
  const [layout, setLayout] = useState({ width: 0, height: 0 });

  if (!points) return null;

  const connections = [
    ["left_shoulder", "right_shoulder"],
    ["left_shoulder", "left_elbow"],
    ["left_elbow", "left_wrist"],
    ["right_shoulder", "right_elbow"],
    ["right_elbow", "right_wrist"],
    ["left_shoulder", "left_hip"],
    ["right_shoulder", "right_hip"],
    ["left_hip", "right_hip"],
    ["left_hip", "left_knee"],
    ["left_knee", "left_ankle"],
    ["right_hip", "right_knee"],
    ["right_knee", "right_ankle"],
  ];

  const color = isFormCorrect ? "#10B981" : "#EF4444";

  return (
    <View
      style={StyleSheet.absoluteFillObject}
      onLayout={(e) =>
        setLayout({
          width: e.nativeEvent.layout.width,
          height: e.nativeEvent.layout.height,
        })
      }
    >
      {layout.width > 0 && layout.height > 0 && (
        <>
          {connections.map(([p1, p2], idx) => {
            const pt1 = points[p1];
            const pt2 = points[p2];
            if (!pt1 || !pt2) return null;

            const x1 = pt1.x * layout.width;
            const y1 = pt1.y * layout.height;
            const x2 = pt2.x * layout.width;
            const y2 = pt2.y * layout.height;

            const dx = x2 - x1;
            const dy = y2 - y1;
            const length = Math.sqrt(dx * dx + dy * dy);
            const angle = Math.atan2(dy, dx);

            const cx = (x1 + x2) / 2;
            const cy = (y1 + y2) / 2;

            return (
              <View
                key={`bone-${idx}`}
                style={{
                  position: "absolute",
                  left: cx - length / 2,
                  top: cy - 2,
                  width: length,
                  height: 4,
                  backgroundColor: color,
                  transform: [{ rotate: `${angle}rad` }],
                  borderRadius: 2,
                }}
              />
            );
          })}

          {Object.entries(points).map(([key, pt]: any) => {
            const x = pt.x * layout.width;
            const y = pt.y * layout.height;
            return (
              <View
                key={`joint-${key}`}
                style={{
                  position: "absolute",
                  left: x - 6,
                  top: y - 6,
                  width: 12,
                  height: 12,
                  borderRadius: 6,
                  backgroundColor: color,
                  borderWidth: 2,
                  borderColor: "#FFFFFF",
                }}
              />
            );
          })}
        </>
      )}
    </View>
  );
};

export const PoseScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [isActive, setIsActive] = useState(false);
  const [exerciseMode, setExerciseMode] = useState<ExerciseMode>("squat");
  const [kneeAngle, setKneeAngle] = useState<number | string>("--");
  const [backAngle, setBackAngle] = useState<number | string>("--");
  const [elbowAngle, setElbowAngle] = useState<number | string>("--");
  const [feedback, setFeedback] = useState<string[]>([]);
  const [isFormCorrect, setIsFormCorrect] = useState(true);
  const [sdkLoaded, setSdkLoaded] = useState(false);
  const [isModelReady, setIsModelReady] = useState(false);
  const [isWasmLoaded, setIsWasmLoaded] = useState(false);
  const [simulatedPoints, setSimulatedPoints] = useState<any>(null);

  const videoRef = useRef<any>(null);
  const canvasRef = useRef<any>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const poseRef = useRef<any>(null);
  const activeRef = useRef(false);
  const exerciseModeRef = useRef<ExerciseMode>("squat");
  const animationFrameRef = useRef<number | null>(null);
  const simulationIntervalRef = useRef<any>(null);

  useEffect(() => {
    exerciseModeRef.current = exerciseMode;
    if (isActive) {
      setKneeAngle("--");
      setBackAngle("--");
      setElbowAngle("--");
      setFeedback([]);
      setIsFormCorrect(true);
    }
  }, [exerciseMode]);

  useEffect(() => {
    const getPermissions = async () => {
      if (Platform.OS !== "web") {
        const { status } = await Camera.requestCameraPermissionsAsync();
        setHasPermission(status === "granted");
      } else {
        setHasPermission(true);
        loadMediaPipe();
      }
    };
    getPermissions();

    return () => {
      stopSession();
    };
  }, []);

  const loadMediaPipe = async () => {
    if (Platform.OS !== "web") return;
    try {
      await loadScript("https://cdn.jsdelivr.net/npm/@mediapipe/camera_utils/camera_utils.js");
      await loadScript("https://cdn.jsdelivr.net/npm/@mediapipe/pose/pose.js");
      setSdkLoaded(true);
      initializePoseModel();
    } catch {}
  };

  const loadScript = (src: string): Promise<void> => {
    return new Promise((resolve, reject) => {
      const existing = document.querySelector(`script[src="${src}"]`);
      if (existing) {
        resolve();
        return;
      }
      const script = document.createElement("script");
      script.src = src;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject();
      document.head.appendChild(script);
    });
  };

  const initializePoseModel = () => {
    const win = window as any;
    if (!win.Pose) return;

    const pose = new win.Pose({
      locateFile: (file: string) => `https://cdn.jsdelivr.net/npm/@mediapipe/pose/${file}`,
    });

    pose.setOptions({
      modelComplexity: 1,
      smoothLandmarks: true,
      minDetectionConfidence: 0.5,
      minTrackingConfidence: 0.5,
    });

    pose.onResults(onPoseResults);
    poseRef.current = pose;
    setIsModelReady(true);
  };

  const calculateAngle = (a: any, b: any, c: any): number => {
    const ba = { x: a.x - b.x, y: a.y - b.y };
    const bc = { x: c.x - b.x, y: c.y - b.y };
    const dotProd = ba.x * bc.x + ba.y * bc.y;
    const magBa = Math.sqrt(ba.x * ba.x + ba.y * ba.y);
    const magBc = Math.sqrt(bc.x * bc.x + bc.y * bc.y);
    if (magBa === 0 || magBc === 0) return 0.0;
    let cosAngle = dotProd / (magBa * magBc);
    cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
    return Math.round(Math.acos(cosAngle) * (180 / Math.PI) * 10) / 10;
  };

  const processForm = (points: any, mode: ExerciseMode) => {
    const alerts: string[] = [];
    let correct = true;

    const knee = calculateAngle(points.left_hip, points.left_knee, points.left_ankle);
    const back = calculateAngle(points.left_shoulder, points.left_hip, points.left_knee);
    const elbow = calculateAngle(points.left_shoulder, points.left_elbow, points.left_wrist);

    setKneeAngle(knee);
    setBackAngle(back);
    setElbowAngle(elbow);

    if (mode === "squat") {
      const hipWidth = Math.abs(points.left_hip.x - points.right_hip.x);
      const kneeWidth = Math.abs(points.left_knee.x - points.right_knee.x);
      const isCaving = kneeWidth < hipWidth * 0.92;

      if (isCaving) {
        alerts.push("Knees caving in — push them out.");
        correct = false;
      }
      if (back < 145.0) {
        alerts.push("Keep your back straight.");
        correct = false;
      }
      if (correct) {
        if (knee < 100.0) {
          alerts.push("Good depth!");
        } else {
          alerts.push("Squat: Lower your hips.");
        }
      }
    } else {
      const leftElbowDrift = Math.abs(points.left_elbow.x - points.left_shoulder.x);
      const isDrifting = leftElbowDrift > 0.12;

      if (isDrifting) {
        alerts.push("Keep elbows tucked to your side.");
        correct = false;
      }
      if (back < 160.0) {
        alerts.push("Avoid leaning back.");
        correct = false;
      }
      if (correct) {
        if (elbow < 60.0) {
          alerts.push("Good squeeze at top!");
        } else {
          alerts.push("Curl: Lift weights upward.");
        }
      }
    }

    setFeedback(alerts);
    setIsFormCorrect(correct);
  };

  const onPoseResults = (results: any) => {
    if (!activeRef.current) return;
    if (!isWasmLoaded) {
      setIsWasmLoaded(true);
    }
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    if (results.poseLandmarks) {
      const lms = results.poseLandmarks;
      const points = {
        left_shoulder: lms[11],
        right_shoulder: lms[12],
        left_elbow: lms[13],
        right_elbow: lms[14],
        left_wrist: lms[15],
        right_wrist: lms[16],
        left_hip: lms[23],
        right_hip: lms[24],
        left_knee: lms[25],
        right_knee: lms[26],
        left_ankle: lms[27],
        right_ankle: lms[28],
      };

      processForm(points, exerciseModeRef.current);
      drawSkeletonOnCanvas(ctx, points);
    }
  };

  const drawSkeletonOnCanvas = (ctx: any, points: any) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const videoWidth = videoRef.current ? videoRef.current.videoWidth : 640;
    const videoHeight = videoRef.current ? videoRef.current.videoHeight : 480;
    const containerWidth = canvas.clientWidth;
    const containerHeight = canvas.clientHeight;

    if (canvas.width !== containerWidth || canvas.height !== containerHeight) {
      canvas.width = containerWidth;
      canvas.height = containerHeight;
    }

    const arVideo = videoWidth / videoHeight;
    const arContainer = containerWidth / containerHeight;

    let scale = 1;
    let offsetX = 0;
    let offsetY = 0;

    if (arContainer < arVideo) {
      scale = containerHeight / videoHeight;
      offsetX = (containerWidth - videoWidth * scale) / 2;
    } else {
      scale = containerWidth / videoWidth;
      offsetY = (containerHeight - videoHeight * scale) / 2;
    }

    ctx.fillStyle = isFormCorrect ? "#10B981" : "#EF4444";
    ctx.strokeStyle = isFormCorrect ? "#10B981" : "#EF4444";
    ctx.lineWidth = 4;

    const connections = [
      ["left_shoulder", "right_shoulder"],
      ["left_shoulder", "left_elbow"],
      ["left_elbow", "left_wrist"],
      ["right_shoulder", "right_elbow"],
      ["right_elbow", "right_wrist"],
      ["left_shoulder", "left_hip"],
      ["right_shoulder", "right_hip"],
      ["left_hip", "right_hip"],
      ["left_hip", "left_knee"],
      ["left_knee", "left_ankle"],
      ["right_hip", "right_knee"],
      ["right_knee", "right_ankle"],
    ];

    const screenPoints: any = {};
    for (const [key, pt] of Object.entries(points)) {
      const point = pt as any;
      screenPoints[key] = {
        x: point.x * videoWidth * scale + offsetX,
        y: point.y * videoHeight * scale + offsetY,
      };
    }

    connections.forEach(([p1, p2]) => {
      const pt1 = screenPoints[p1];
      const pt2 = screenPoints[p2];
      if (pt1 && pt2) {
        ctx.beginPath();
        ctx.moveTo(pt1.x, pt1.y);
        ctx.lineTo(pt2.x, pt2.y);
        ctx.stroke();
      }
    });

    for (const pt of Object.values(screenPoints)) {
      const point = pt as any;
      ctx.beginPath();
      ctx.arc(point.x, point.y, 6, 0, 2 * Math.PI);
      ctx.fill();
    }
  };

  const startSession = async () => {
    setIsActive(true);
    activeRef.current = true;

    if (Platform.OS === "web") {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: "user" },
          audio: false,
        });
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play();
        }
        processWebFrame();
      } catch {
        setIsActive(false);
        activeRef.current = false;
      }
    } else {
      let step = 0;
      simulationIntervalRef.current = setInterval(() => {
        step += 0.08;
        const currentMode = exerciseModeRef.current;
        const cycle = Math.sin(step);
        let correct = true;
        const alerts: string[] = [];

        // Base points in percentage (0 to 1)
        const points: any = {
          left_shoulder: { x: 0.35, y: 0.25 },
          right_shoulder: { x: 0.65, y: 0.25 },
          left_elbow: { x: 0.28, y: 0.42 },
          right_elbow: { x: 0.72, y: 0.42 },
          left_wrist: { x: 0.28, y: 0.58 },
          right_wrist: { x: 0.72, y: 0.58 },
          left_hip: { x: 0.40, y: 0.55 },
          right_hip: { x: 0.60, y: 0.55 },
          left_knee: { x: 0.40, y: 0.72 },
          right_knee: { x: 0.60, y: 0.72 },
          left_ankle: { x: 0.40, y: 0.88 },
          right_ankle: { x: 0.60, y: 0.88 },
        };

        if (currentMode === "squat") {
          const knee = Math.round(130 + 40 * cycle);
          const back = cycle < -0.5 ? Math.round(135 + 5 * cycle) : Math.round(155 + 5 * cycle);
          const caveToggle = cycle > 0.6;

          setKneeAngle(knee);
          setBackAngle(back);
          setElbowAngle("--");

          const depth = (1 - cycle) / 2; // 0 to 1
          points.left_hip.y = 0.55 + 0.15 * depth;
          points.right_hip.y = 0.55 + 0.15 * depth;
          points.left_knee.y = 0.72 + 0.05 * depth;
          points.right_knee.y = 0.72 + 0.05 * depth;
          points.left_shoulder.y = 0.25 + 0.18 * depth;
          points.right_shoulder.y = 0.25 + 0.18 * depth;
          points.left_elbow.y = 0.42 + 0.18 * depth;
          points.right_elbow.y = 0.42 + 0.18 * depth;
          points.left_wrist.y = 0.58 + 0.18 * depth;
          points.right_wrist.y = 0.58 + 0.18 * depth;

          if (caveToggle) {
            alerts.push("Knees caving in — push them out.");
            correct = false;
            points.left_knee.x = 0.45;
            points.right_knee.x = 0.55;
          }
          if (back < 145) {
            alerts.push("Keep your back straight.");
            correct = false;
            points.left_shoulder.x = 0.30;
            points.right_shoulder.x = 0.60;
          }
          if (correct) {
            if (knee < 100) {
              alerts.push("Good depth!");
            } else {
              alerts.push("Squat: Lower your hips.");
            }
          }
        } else {
          const elbow = Math.round(100 + 60 * cycle);
          const back = cycle < -0.6 ? Math.round(150 + 5 * cycle) : Math.round(170 + 5 * cycle);
          const driftToggle = cycle > 0.7;

          setKneeAngle("--");
          setBackAngle(back);
          setElbowAngle(elbow);

          const curlProgress = (1 - cycle) / 2; // 0 to 1
          points.left_wrist.y = 0.58 - 0.28 * curlProgress;
          points.right_wrist.y = 0.58 - 0.28 * curlProgress;
          points.left_wrist.x = 0.28 + 0.05 * curlProgress;
          points.right_wrist.x = 0.72 - 0.05 * curlProgress;

          if (driftToggle) {
            alerts.push("Keep elbows tucked to your side.");
            correct = false;
            points.left_elbow.x = 0.22;
            points.right_elbow.x = 0.78;
          }
          if (back < 160) {
            alerts.push("Avoid leaning back.");
            correct = false;
            points.left_shoulder.x = 0.38;
            points.right_shoulder.x = 0.62;
          }
          if (correct) {
            if (elbow < 60) {
              alerts.push("Good squeeze at top!");
            } else {
              alerts.push("Curl: Lift weights upward.");
            }
          }
        }

        setFeedback(alerts);
        setIsFormCorrect(correct);
        setSimulatedPoints(points);
      }, 150);
    }
  };

  const processWebFrame = async () => {
    if (!activeRef.current) return;
    if (videoRef.current && poseRef.current && videoRef.current.readyState >= 2) {
      try {
        await poseRef.current.send({ image: videoRef.current });
      } catch {}
    }
    animationFrameRef.current = requestAnimationFrame(processWebFrame);
  };

  const stopSession = () => {
    setIsActive(false);
    activeRef.current = false;
    setIsWasmLoaded(false);
    setSimulatedPoints(null);
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    if (simulationIntervalRef.current) {
      clearInterval(simulationIntervalRef.current);
      simulationIntervalRef.current = null;
    }
    if (Platform.OS === "web") {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
      }
      if (videoRef.current) {
        videoRef.current.srcObject = null;
      }
      if (canvasRef.current) {
        const ctx = canvasRef.current.getContext("2d");
        ctx?.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height);
      }
    }
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
        {Platform.OS === "web" ? (
          <View style={styles.fullscreenWebCamera}>
            <video
              ref={videoRef}
              style={styles.webVideoElement}
              playsInline
              muted
            />
            <canvas
              ref={canvasRef}
              style={styles.webCanvasOverlay}
            />
          </View>
        ) : (
          <View style={StyleSheet.absoluteFillObject}>
            <CameraView style={styles.fullscreenNativeCamera} facing="front" />
            {isActive && simulatedPoints && (
              <SkeletonOverlay points={simulatedPoints} isFormCorrect={isFormCorrect} />
            )}
          </View>
        )}

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

        {Platform.OS === "web" && isActive && !isWasmLoaded && (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator size="large" color="#FFFFFF" style={styles.spinner} />
            <Text style={styles.loadingOverlayText}>
              Loading WASM Model...{"\n"}Please wait a moment.
            </Text>
          </View>
        )}

        {Platform.OS !== "web" && isActive && (
          <View style={styles.simulatedIndicator}>
            <Text style={styles.simulatedText}>Simulation Mode</Text>
          </View>
        )}

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
            disabled={Platform.OS === "web" && !isModelReady}
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
  fullscreenNativeCamera: {
    position: "absolute",
    top: 0,
    left: 0,
    width: "100%",
    height: "100%",
  },
  headerOverlay: {
    position: "absolute",
    top: Platform.OS === "ios" ? 10 : 20,
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
    backgroundColor: "rgba(15, 23, 42, 0.65)",
    borderRadius: SIZES.radiusSm,
    paddingVertical: SPACING.sm,
    marginHorizontal: 4,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.15)",
  },
  statLabel: {
    fontSize: 9,
    fontWeight: "600",
    color: "rgba(255, 255, 255, 0.6)",
    letterSpacing: 0.5,
    marginBottom: 2,
  },
  statVal: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#FFFFFF",
  },
  feedbackPanel: {
    backgroundColor: "rgba(15, 23, 42, 0.65)",
    borderRadius: SIZES.radiusSm,
    padding: SPACING.md,
    marginBottom: SPACING.md,
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.15)",
    minHeight: 70,
  },
  feedbackPanelError: {
    backgroundColor: "rgba(239, 68, 68, 0.2)",
    borderColor: "rgba(239, 68, 68, 0.5)",
  },
  panelTitle: {
    fontSize: 11,
    fontWeight: "600",
    color: "rgba(255, 255, 255, 0.5)",
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginBottom: 6,
  },
  noFeedback: {
    fontSize: 13,
    color: "rgba(255, 255, 255, 0.7)",
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
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  startButton: {
    backgroundColor: "#FFFFFF",
  },
  stopButton: {
    backgroundColor: "#EF4444",
  },
  sessionButtonText: {
    color: "#0F172A",
    fontSize: 14,
    fontWeight: "600",
  },
});
