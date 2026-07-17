from typing import Any
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from app.api import deps
from app.core import security
from app.models.user import serialize_user
from app.schemas.user import User as UserSchema, UserCreate, Token, UserLogin

router = APIRouter()

@router.post("/signup", response_model=UserSchema)
async def signup(user_in: UserCreate, db = Depends(deps.get_db)) -> Any:
    user = await db["users"].find_one({"email": user_in.email})
    if user:
        raise HTTPException(
            status_code=400,
            detail="The user with this email already exists in the system.",
        )
    user_dict = {
        "email": user_in.email,
        "hashed_password": security.get_password_hash(user_in.password),
        "full_name": user_in.full_name,
        "is_active": True,
        "weight_kg": user_in.weight_kg,
        "height_cm": user_in.height_cm,
        "gender": user_in.gender,
        "created_at": datetime.now(timezone.utc),
    }
    result = await db["users"].insert_one(user_dict)
    user_dict["_id"] = result.inserted_id
    return serialize_user(user_dict)

@router.post("/login", response_model=Token)
async def login(user_in: UserLogin, db = Depends(deps.get_db)) -> Any:
    user = await db["users"].find_one({"email": user_in.email})
    if not user or not security.verify_password(user_in.password, user["hashed_password"]):
        raise HTTPException(
            status_code=400,
            detail="Incorrect email or password"
        )
    elif not user.get("is_active", True):
        raise HTTPException(
            status_code=400,
            detail="Inactive user"
        )
    return {
        "access_token": security.create_access_token(subject=str(user["_id"])),
        "token_type": "bearer",
    }

@router.post("/login-oauth", response_model=Token)
async def login_oauth(form_data: OAuth2PasswordRequestForm = Depends(), db = Depends(deps.get_db)) -> Any:
    user = await db["users"].find_one({"email": form_data.username})
    if not user or not security.verify_password(form_data.password, user["hashed_password"]):
        raise HTTPException(
            status_code=400,
            detail="Incorrect email or password"
        )
    elif not user.get("is_active", True):
        raise HTTPException(
            status_code=400,
            detail="Inactive user"
        )
    return {
        "access_token": security.create_access_token(subject=str(user["_id"])),
        "token_type": "bearer",
    }

@router.get("/me", response_model=UserSchema)
async def read_user_me(current_user = Depends(deps.get_current_user)) -> Any:
    return current_user
