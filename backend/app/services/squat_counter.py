from mediapipe.tasks import python
from mediapipe.tasks.python import vision
import mediapipe as mp
import numpy as np
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

MODEL_PATH = BASE_DIR.parent / "models" / "pose_landmarker_heavy.task"

base_options = python.BaseOptions(
    model_asset_path=str(MODEL_PATH),
    delegate=python.BaseOptions.Delegate.CPU
)

options = vision.PoseLandmarkerOptions(
    base_options=base_options,
    running_mode=vision.RunningMode.VIDEO
)

pose_landmarker = vision.PoseLandmarker.create_from_options(options)

counter = 0
stage = None
timestamp = 0


def calculate_angle(a, b, c):
    a = np.array(a)
    b = np.array(b)
    c = np.array(c)

    radians = np.arctan2(c[1]-b[1], c[0]-b[0]) - \
              np.arctan2(a[1]-b[1], a[0]-b[0])

    angle = np.abs(radians * 180 / np.pi)

    if angle > 180:
        angle = 360-angle

    return angle


def process_frame(frame):

    global counter, stage, timestamp

    frame_rgb = frame[:, :, ::-1]

    mp_image = mp.Image(
        image_format=mp.ImageFormat.SRGB,
        data=frame_rgb
    )

    result = pose_landmarker.detect_for_video(
        mp_image,
        timestamp
    )

    timestamp += 33

    if not result.pose_landmarks:
        return {
            "reps": counter,
            "stage": stage
        }

    landmarks = result.pose_landmarks[0]

    h, w, _ = frame.shape

    hip = [landmarks[23].x * w, landmarks[23].y * h]
    knee = [landmarks[25].x * w, landmarks[25].y * h]
    ankle = [landmarks[27].x * w, landmarks[27].y * h]

    angle = calculate_angle(hip, knee, ankle)

    if angle > 160:
        stage = "up"

    if angle < 90 and stage == "up":
        stage = "down"
        counter += 1

    return {
        "reps": counter,
        "stage": stage,
        "angle": int(angle)
    }