from typing import Any, List
from datetime import datetime, timezone
from bson import ObjectId
from fastapi import APIRouter, Depends, HTTPException, status
from app.api import deps
from app.schemas.workout import WorkoutCreate, WorkoutResponse, WorkoutStats

router = APIRouter()

def serialize_workout(doc: Any) -> Any:
    return {
        "id": str(doc["_id"]),
        "user_id": str(doc["user_id"]),
        "exercise": doc["exercise"],
        "sets": doc["sets"],
        "reps": doc["reps"],
        "weight": doc["weight"],
        "duration_minutes": doc["duration_minutes"],
        "calories_burned": doc["calories_burned"],
        "intensity_score": doc["intensity_score"],
        "timestamp": doc["timestamp"],
    }

@router.post("/", response_model=WorkoutResponse)
async def create_workout(workout_in: WorkoutCreate, current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    weight = current_user.get("weight_kg", 70.0)
    if not weight or weight <= 0:
        weight = 70.0

    met = 4.0
    if workout_in.exercise.lower() == "squat":
        met = 5.0
    elif workout_in.exercise.lower() == "curl":
        met = 3.5

    duration = workout_in.sets * 2
    calories = met * weight * (duration / 60.0)
    volume = workout_in.sets * workout_in.reps * workout_in.weight
    intensity = (volume / weight) * met

    workout_dict = {
        "user_id": ObjectId(current_user["id"]),
        "exercise": workout_in.exercise,
        "sets": workout_in.sets,
        "reps": workout_in.reps,
        "weight": workout_in.weight,
        "duration_minutes": duration,
        "calories_burned": round(calories, 2),
        "intensity_score": round(intensity, 2),
        "timestamp": datetime.now(timezone.utc),
    }

    result = await db["workouts"].insert_one(workout_dict)
    workout_dict["_id"] = result.inserted_id
    return serialize_workout(workout_dict)

@router.get("/", response_model=List[WorkoutResponse])
async def list_workouts(current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    cursor = db["workouts"].find({"user_id": ObjectId(current_user["id"])}).sort("timestamp", -1)
    workouts = await cursor.to_list(length=100)
    return [serialize_workout(w) for w in workouts]

@router.get("/stats", response_model=WorkoutStats)
async def get_workout_stats(current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    cursor = db["workouts"].find({"user_id": ObjectId(current_user["id"])})
    workouts = await cursor.to_list(length=1000)

    total_workouts = len(workouts)
    total_calories = sum(w.get("calories_burned", 0.0) for w in workouts)
    total_sets = sum(w.get("sets", 0) for w in workouts)
    total_reps = sum(w.get("reps", 0) for w in workouts)
    
    total_intensity = sum(w.get("intensity_score", 0.0) for w in workouts)
    average_intensity = round(total_intensity / total_workouts, 2) if total_workouts > 0 else 0.0

    return {
        "total_workouts": total_workouts,
        "total_calories": round(total_calories, 2),
        "total_sets": total_sets,
        "total_reps": total_reps,
        "average_intensity": average_intensity,
    }
