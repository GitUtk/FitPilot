from fastapi import APIRouter
from app.api.v1.endpoints import auth, workouts, meals

api_router = APIRouter()
api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(workouts.router, prefix="/workouts", tags=["workouts"])
api_router.include_router(meals.router, prefix="/meals", tags=["meals"])
