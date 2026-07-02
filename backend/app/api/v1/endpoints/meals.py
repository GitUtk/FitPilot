import httpx
import re
import json
import traceback
from typing import Any, List, Dict
from datetime import datetime, time, timezone
from bson import ObjectId
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from app.api import deps
from app.core.config import settings
from app.schemas.meal import MealCreate, MealResponse, AdaptationResponse

router = APIRouter()

class ChatTextRequest(BaseModel):
    text: str

class MealChatMessageResponse(BaseModel):
    id: str
    role: str
    text: str
    timestamp: datetime

def serialize_chat_msg(doc: Any) -> Any:
    return {
        "id": str(doc["_id"]),
        "role": doc["role"],
        "text": doc["text"],
        "timestamp": doc["timestamp"],
    }

def serialize_meal(doc: Any) -> Any:
    return {
        "id": str(doc["_id"]),
        "user_id": str(doc["user_id"]),
        "description": doc["description"],
        "calories": doc["calories"],
        "protein": doc["protein"],
        "carbs": doc["carbs"],
        "fat": doc["fat"],
        "timestamp": doc["timestamp"],
    }

@router.get("/chat", response_model=List[MealChatMessageResponse])
async def get_chat_history(current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    cursor = db["meal_chats"].find({"user_id": ObjectId(current_user["id"])}).sort("timestamp", 1)
    messages = await cursor.to_list(length=200)
    return [serialize_chat_msg(m) for m in messages]

@router.delete("/chat")
async def clear_chat_history(current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    await db["meal_chats"].delete_many({"user_id": ObjectId(current_user["id"])})
    return {"status": "success"}

@router.post("/chat")
async def chat_meal_logging(payload: ChatTextRequest, current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    if not settings.GEMINI_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Gemini API Key is not configured on the server."
        )

    user_msg = {
        "user_id": ObjectId(current_user["id"]),
        "role": "user",
        "text": payload.text,
        "timestamp": datetime.now(timezone.utc),
    }
    await db["meal_chats"].insert_one(user_msg)

    cursor = db["meal_chats"].find({"user_id": ObjectId(current_user["id"])}).sort("timestamp", 1)
    history_docs = await cursor.to_list(length=100)

    contents = []
    for doc in history_docs:
        contents.append({
            "role": doc["role"],
            "parts": [{"text": doc["text"]}]
        })

    system_instruction = (
        "You are FitPilot's expert nutrition assistant specializing in Indian cuisine and nutrition. "
        "Your task is to help the user log their meals in plain text. "
        "Follow these rules strictly:\n"
        "1. Map what they ate to ICMR (Indian Council of Medical Research) standard portion sizes.\n"
        "2. Provide nutritional values: Calories (kcal), Protein (g), Carbohydrates (g), and Fats (g).\n"
        "3. If the portion size, quantity, or specific food detail is unclear, ask exactly ONE clarifying question. E.g., 'Did you have 1 or 2 medium rotis?'\n"
        "4. Be transparent and honest about approximations made in calculations.\n"
        "5. Output your analysis in a clear, bulleted summary format. Keep responses brief and clean to use minimum tokens.\n"
        "6. If you have enough details to log the meal, you MUST append a tag at the very end of your response: "
        "<meal_log>{\"description\": \"brief summary of food items\", \"calories\": 123.0, \"protein\": 12.0, \"carbs\": 34.0, \"fat\": 5.0}</meal_log>. "
        "If you are asking a clarifying question or details are missing, do NOT append any <meal_log> tag."
    )

    gemini_payload = {
        "contents": contents,
        "systemInstruction": {
            "parts": [{"text": system_instruction}]
        }
    }

    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={settings.GEMINI_API_KEY}"

    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(url, json=gemini_payload, timeout=20.0)
            if response.status_code != 200:
                raise HTTPException(
                    status_code=response.status_code,
                    detail=f"Gemini API returned an error: {response.text}"
                )
            result = response.json()
            candidates = result.get("candidates", [])
            if not candidates:
                raise HTTPException(
                    status_code=502,
                    detail="Failed to generate nutritional analysis."
                )
            content_res = candidates[0].get("content", {})
            parts = content_res.get("parts", [])
            if not parts:
                raise HTTPException(
                    status_code=502,
                    detail="Empty response parts from Gemini."
                )
            text_res = parts[0].get("text", "")
            
            match = re.search(r"<meal_log>(.*?)</meal_log>", text_res, re.DOTALL)
            if match:
                log_json_str = match.group(1).strip()
                try:
                    log_data = json.loads(log_json_str)
                    meal_dict = {
                        "user_id": ObjectId(current_user["id"]),
                        "description": log_data.get("description", "Logged Meal"),
                        "calories": float(log_data.get("calories", 0)),
                        "protein": float(log_data.get("protein", 0)),
                        "carbs": float(log_data.get("carbs", 0)),
                        "fat": float(log_data.get("fat", 0)),
                        "timestamp": datetime.now(timezone.utc),
                    }
                    await db["meals"].insert_one(meal_dict)
                except Exception as log_exc:
                    print(f"Failed to auto-log meal: {log_exc}")
                
                text_res = re.sub(r"<meal_log>.*?</meal_log>", "", text_res, flags=re.DOTALL).strip()

            model_msg = {
                "user_id": ObjectId(current_user["id"]),
                "role": "model",
                "text": text_res,
                "timestamp": datetime.now(timezone.utc),
            }
            await db["meal_chats"].insert_one(model_msg)

            return serialize_chat_msg(model_msg)
        except Exception as exc:
            traceback.print_exc()
            raise HTTPException(
                status_code=503,
                detail=f"Failed to connect to the AI service: {repr(exc)}"
            )

@router.post("/", response_model=MealResponse)
async def log_meal(meal_in: MealCreate, current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    meal_dict = {
        "user_id": ObjectId(current_user["id"]),
        "description": meal_in.description,
        "calories": meal_in.calories,
        "protein": meal_in.protein,
        "carbs": meal_in.carbs,
        "fat": meal_in.fat,
        "timestamp": datetime.now(timezone.utc),
    }
    result = await db["meals"].insert_one(meal_dict)
    meal_dict["_id"] = result.inserted_id
    return serialize_meal(meal_dict)

@router.get("/", response_model=List[MealResponse])
async def list_meals(current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    cursor = db["meals"].find({"user_id": ObjectId(current_user["id"])}).sort("timestamp", -1)
    meals = await cursor.to_list(length=100)
    return [serialize_meal(m) for m in meals]

@router.get("/adaptation", response_model=AdaptationResponse)
async def get_adaptation_advice(current_user = Depends(deps.get_current_user), db = Depends(deps.get_db)) -> Any:
    try:
        today_dt = datetime.now(timezone.utc)
        start_of_day = datetime.combine(today_dt.date(), time.min, tzinfo=timezone.utc)

        cursor_workouts = db["workouts"].find({
            "user_id": ObjectId(current_user["id"]),
            "timestamp": {"$gte": start_of_day}
        })
        workouts = await cursor_workouts.to_list(length=100)

        cursor_meals = db["meals"].find({
            "user_id": ObjectId(current_user["id"]),
            "timestamp": {"$gte": start_of_day}
        })
        meals = await cursor_meals.to_list(length=100)

        weight = current_user.get("weight_kg", 70.0)

        total_workout_calories = 0.0
        for w in workouts:
            total_workout_calories += w.get("calories_burned", 0.0)

        total_meal_calories = 0.0
        total_protein = 0.0
        total_carbs = 0.0
        total_fat = 0.0
        for m in meals:
            total_meal_calories += m.get("calories", 0.0)
            total_protein += m.get("protein", 0.0)
            total_carbs += m.get("carbs", 0.0)
            total_fat += m.get("fat", 0.0)

        if not workouts and not meals:
            empty_msg = (
                "No activity recorded today yet. Log your exercises or chat to log meals in the other tabs, "
                "and this panel will compute remaining macro allowances."
            )
            await db["users"].update_one(
                {"_id": ObjectId(current_user["id"])},
                {"$set": {
                    "latest_adaptation": {
                        "recommendation": empty_msg,
                        "timestamp": datetime.now(timezone.utc)
                    }
                }}
            )
            return {"recommendation": empty_msg}

        target_calories = 2000.0 + total_workout_calories
        target_protein = weight * 1.6
        target_carbs = weight * 3.0
        target_fat = weight * 1.0

        cal_diff = target_calories - total_meal_calories
        prot_diff = target_protein - total_protein
        carbs_diff = target_carbs - total_carbs
        fat_diff = target_fat - total_fat

        lines = []
        lines.append(f"Based on today's logs (Burned: {total_workout_calories:.0f} kcal, Consumed: {total_meal_calories:.0f} kcal):\n")

        if cal_diff > 100:
            lines.append(f"• Energy Demand: Consuming an additional {cal_diff:.0f} kcal is recommended to meet metabolic targets.")
        elif cal_diff < -100:
            lines.append(f"• Energy Surplus: You are in a calorie surplus of {abs(cal_diff):.0f} kcal. Focus on hydration.")
        else:
            lines.append("• Energy Balance: Your daily calorie intake matches your metabolic demand.")

        if prot_diff > 5:
            lines.append(f"• Protein Shortfall: You need {prot_diff:.0f}g more protein. Active muscle recovery requires amino acids.")
        else:
            lines.append("• Protein Target: Met! Protein levels are sufficient for protein synthesis.")

        if carbs_diff > 10:
            lines.append(f"• Carbohydrate Needs: You need {carbs_diff:.0f}g more carbs to replenish muscle glycogen stores.")
        else:
            lines.append("• Carbohydrate Target: Met. Keep further carb intake minimal.")

        if fat_diff > 5:
            lines.append(f"• Lipids Level: You need {fat_diff:.0f}g more healthy fats for hormonal support.")

        advice_text = "\n".join(lines)
        
        await db["users"].update_one(
            {"_id": ObjectId(current_user["id"])},
            {"$set": {
                "latest_adaptation": {
                    "recommendation": advice_text,
                    "timestamp": datetime.now(timezone.utc)
                }
            }}
        )

        return {"recommendation": advice_text}

    except Exception as exc:
        traceback.print_exc()
        return {
            "recommendation": f"Failed to compute adaptation details: {repr(exc)}"
        }
