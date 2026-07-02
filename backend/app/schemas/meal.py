from datetime import datetime
from pydantic import BaseModel, ConfigDict

class MealBase(BaseModel):
    description: str
    calories: float
    protein: float
    carbs: float
    fat: float

class MealCreate(MealBase):
    pass

class MealResponse(MealBase):
    id: str
    user_id: str
    timestamp: datetime

    model_config = ConfigDict(from_attributes=True)

class AdaptationResponse(BaseModel):
    recommendation: str
