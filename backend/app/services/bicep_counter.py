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


left_counter = 0
right_counter = 0
left_stage = None
right_stage = None
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
    global left_counter,right_counter,left_stage,right_stage,timestamp

    mp_image = mp.Image(
        image_format=mp.ImageFormat.SRGB,
        data=frame
    )

    result = pose_landmarker.detect_for_video(
        mp_image,
        timestamp
    )

    timestamp += 33

    if not result.pose_landmarks:
        return {
            "left_reps": left_counter,
            "right_reps": right_counter,
            "left_stage": left_stage,
            "right_stage": right_stage
        }

    landmarks = result.pose_landmarks[0]

    h,w,_ = frame.shape

    left_shoulder=[landmarks[11].x*w,landmarks[11].y*h]
    left_elbow=[landmarks[13].x*w,landmarks[13].y*h]
    left_wrist=[landmarks[15].x*w,landmarks[15].y*h]

    right_shoulder=[landmarks[12].x*w,landmarks[12].y*h]
    right_elbow=[landmarks[14].x*w,landmarks[14].y*h]
    right_wrist=[landmarks[16].x*w,landmarks[16].y*h]

    left_angle=calculate_angle(left_shoulder,left_elbow,left_wrist)
    right_angle=calculate_angle(right_shoulder,right_elbow,right_wrist)

    if left_angle>160:
        left_stage="down"

    if left_angle<30 and left_stage=="down":
        left_stage="up"
        left_counter+=1

    if right_angle>160:
        right_stage="down"

    if right_angle<30 and right_stage=="down":
        right_stage="up"
        right_counter+=1

    return {
        "left_reps":left_counter,
        "right_reps":right_counter,
        "left_stage":left_stage,
        "right_stage":right_stage,
        "left_angle":int(left_angle),
        "right_angle":int(right_angle)
    }