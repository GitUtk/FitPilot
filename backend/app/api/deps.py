from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import jwt, JWTError
from bson import ObjectId
from app.core.config import settings
from app.core.database import db
from app.models.user import serialize_user
from app.schemas.user import TokenPayload

oauth2_scheme = OAuth2PasswordBearer(tokenUrl=f"{settings.API_V1_STR}/auth/login-oauth")

async def get_db():
    yield db

async def get_current_user(db = Depends(get_db), token: str = Depends(oauth2_scheme)):
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=["HS256"])
        token_data = TokenPayload(**payload)
    except (JWTError, Exception):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Could not validate credentials",
        )
    try:
        user_id = ObjectId(token_data.sub)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid token subject")
    
    user_doc = await db["users"].find_one({"_id": user_id})
    if not user_doc:
        raise HTTPException(status_code=404, detail="User not found")
    
    user_data = serialize_user(user_doc)
    if not user_data["is_active"]:
        raise HTTPException(status_code=400, detail="Inactive user")
        
    return user_data
