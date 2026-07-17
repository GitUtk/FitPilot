from datetime import datetime
from typing import Dict, Optional
from pydantic import BaseModel, ConfigDict

class WorkoutBase(BaseModel):
    exercise: str
    sets: int
    reps: int
    weight: float

class WorkoutCreate(WorkoutBase):
    pass

class WorkoutResponse(WorkoutBase):
    id: str
    user_id: str
    duration_minutes: int
    calories_burned: float
    intensity_score: float
    timestamp: datetime

    model_config = ConfigDict(from_attributes=True)

class WorkoutStats(BaseModel):
    total_workouts: int
    total_calories: float
    total_sets: int
    total_reps: int
    total_duration: int = 0
    average_intensity: float
    exercise_breakdown: Optional[Dict[str, Dict[str, int]]] = None
