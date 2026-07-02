export const MOBILE_POSE_HTML = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <style>
    body, html {
      margin: 0;
      padding: 0;
      width: 100%;
      height: 100%;
      overflow: hidden;
      background-color: #000;
    }
    #container {
      position: relative;
      width: 100%;
      height: 100%;
    }
    video {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      transform: scaleX(-1);
    }
    canvas {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      z-index: 2;
    }
    #loading {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      color: #FFFFFF;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      font-size: 16px;
      font-weight: 500;
      z-index: 10;
      text-align: center;
      background-color: rgba(0, 0, 0, 0.6);
      padding: 12px 20px;
      border-radius: 8px;
    }
  </style>
  <script src="https://cdn.jsdelivr.net/npm/@mediapipe/camera_utils/camera_utils.js" crossorigin="anonymous"></script>
  <script src="https://cdn.jsdelivr.net/npm/@mediapipe/pose/pose.js" crossorigin="anonymous"></script>
</head>
<body>
  <div id="container">
    <video id="webcam" autoplay playsinline muted></video>
    <canvas id="output_canvas"></canvas>
    <div id="loading">Initializing Biomechanical Scan...</div>
  </div>
  <script>
    const video = document.getElementById('webcam');
    const canvas = document.getElementById('output_canvas');
    const ctx = canvas.getContext('2d');
    const loadingDiv = document.getElementById('loading');
    const container = document.getElementById('container');

    let exerciseMode = 'squat';
    let isActive = false;

    function sendLog(msg) {
      window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'log', message: msg }));
    }

    function sendPose(knee, back, elbow, correct, alerts) {
      window.ReactNativeWebView.postMessage(JSON.stringify({
        type: 'pose',
        kneeAngle: knee,
        backAngle: back,
        elbowAngle: elbow,
        isFormCorrect: correct,
        feedback: alerts
      }));
    }

    // React Native Communication
    window.addEventListener('message', (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'setup') {
          exerciseMode = data.mode;
          isActive = data.isActive;
        }
      } catch (e) {
        sendLog('Error parsing setup data: ' + e.message);
      }
    });

    // Angle calculation (hip-knee-ankle etc.)
    function calculateAngle(a, b, c) {
      const ba = { x: a.x - b.x, y: a.y - b.y };
      const bc = { x: c.x - b.x, y: c.y - b.y };
      const dotProd = ba.x * bc.x + ba.y * bc.y;
      const magBa = Math.sqrt(ba.x * ba.x + ba.y * ba.y);
      const magBc = Math.sqrt(bc.x * bc.x + bc.y * bc.y);
      if (magBa === 0 || magBc === 0) return 0.0;
      let cosAngle = dotProd / (magBa * magBc);
      cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
      return Math.round(Math.acos(cosAngle) * (180 / Math.PI) * 10) / 10;
    }

    function processForm(points, mode) {
      const alerts = [];
      let correct = true;

      const knee = calculateAngle(points.left_hip, points.left_knee, points.left_ankle);
      const back = calculateAngle(points.left_shoulder, points.left_hip, points.left_knee);
      const elbow = calculateAngle(points.left_shoulder, points.left_elbow, points.left_wrist);

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

      return { knee, back, elbow, correct, alerts };
    }

    function drawSkeleton(points, correct) {
      const w = canvas.width;
      const h = canvas.height;
      
      ctx.clearRect(0, 0, w, h);

      ctx.save();
      // Mirror drawing coordinate system to align with mirrored camera feed
      ctx.translate(w, 0);
      ctx.scale(-1, 1);

      ctx.fillStyle = correct ? "#10B981" : "#EF4444";
      ctx.strokeStyle = correct ? "#10B981" : "#EF4444";
      ctx.lineWidth = 5;

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
        ["right_knee", "right_ankle"]
      ];

      const screenPoints = {};
      for (const [key, pt] of Object.entries(points)) {
        screenPoints[key] = {
          x: pt.x * w,
          y: pt.y * h
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
        ctx.beginPath();
        ctx.arc(pt.x, pt.y, 7, 0, 2 * Math.PI);
        ctx.fill();
      }

      ctx.restore();
    }

    function onPoseResults(results) {
      if (!isActive) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        return;
      }

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
          right_ankle: lms[28]
        };

        const analysis = processForm(points, exerciseMode);
        drawSkeleton(points, analysis.correct);
        sendPose(analysis.knee, analysis.back, analysis.elbow, analysis.correct, analysis.alerts);
      } else {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
      }
    }

    // Load Model
    let pose;
    try {
      pose = new Pose({
        locateFile: (file) => 'https://cdn.jsdelivr.net/npm/@mediapipe/pose/' + file
      });
      pose.setOptions({
        modelComplexity: 1,
        smoothLandmarks: true,
        minDetectionConfidence: 0.5,
        minTrackingConfidence: 0.5
      });
      pose.onResults(onPoseResults);
      sendLog('MediaPipe Pose instance created.');
    } catch (e) {
      sendLog('Failed to initialize MediaPipe Pose: ' + e.message);
    }

    // Launch Video Stream
    navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
      audio: false
    }).then((stream) => {
      video.srcObject = stream;
      video.onloadedmetadata = () => {
        video.play();
        loadingDiv.style.display = 'none';
        
        canvas.width = container.clientWidth;
        canvas.height = container.clientHeight;
        window.addEventListener('resize', () => {
          canvas.width = container.clientWidth;
          canvas.height = container.clientHeight;
        });

        const camera = new Camera(video, {
          onFrame: async () => {
            if (isActive) {
              await pose.send({ image: video });
            }
          },
          width: 640,
          height: 480
        });
        camera.start();
        sendLog('Camera loop started.');
      };
    }).catch((e) => {
      sendLog('Camera access denied: ' + e.message);
      loadingDiv.innerText = 'Camera access required for pose scanning.';
    });
  </script>
</body>
</html>
`;
