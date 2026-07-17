from typing import Any, Dict, Optional

def serialize_user(user_doc: Optional[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    if not user_doc:
        return None
    return {
        "id": str(user_doc["_id"]),
        "email": user_doc["email"],
        "hashed_password": user_doc["hashed_password"],
        "full_name": user_doc.get("full_name"),
        "is_active": user_doc.get("is_active", True),
        "weight_kg": user_doc.get("weight_kg", 70.0),
        "height_cm": user_doc.get("height_cm", 170.0),
        "gender": user_doc.get("gender"),
        "created_at": user_doc.get("created_at"),
    }
