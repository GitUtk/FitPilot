import httpx
import re
import json
import traceback
from typing import Any, List, Dict
from datetime import datetime, time, timezone, timedelta
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

    # Fetch past 7 days of user activity
    seven_days_ago = datetime.now(timezone.utc) - timedelta(days=7)
    
    cursor_past_meals = db["meals"].find({
        "user_id": ObjectId(current_user["id"]),
        "timestamp": {"$gte": seven_days_ago}
    }).sort("timestamp", 1)
    past_meals = await cursor_past_meals.to_list(length=500)

    cursor_past_workouts = db["workouts"].find({
        "user_id": ObjectId(current_user["id"]),
        "timestamp": {"$gte": seven_days_ago}
    }).sort("timestamp", 1)
    past_workouts = await cursor_past_workouts.to_list(length=500)

    # Summarize past 7 days of meals
    meals_summary = []
    for m in past_meals:
        dt_str = m["timestamp"].strftime("%Y-%m-%d")
        meals_summary.append(f"- {dt_str}: {m.get('description', 'Meal')} ({m.get('calories', 0.0)} kcal, P:{m.get('protein', 0.0)}g, C:{m.get('carbs', 0.0)}g, F:{m.get('fat', 0.0)}g)")
    meals_text = "\n".join(meals_summary) if meals_summary else "No meals logged in the last 7 days."

    # Summarize past 7 days of workouts
    workouts_summary = []
    for w in past_workouts:
        dt_str = w["timestamp"].strftime("%Y-%m-%d")
        workouts_summary.append(f"- {dt_str}: {w.get('exercise', 'Workout')} ({w.get('sets', 0)} sets, {w.get('reps', 0)} reps, {w.get('calories_burned', 0.0)} kcal burned)")
    workouts_text = "\n".join(workouts_summary) if workouts_summary else "No workouts logged in the last 7 days."

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
        "You are FitPilot's premium AI Fitness and Nutrition Coach. "
        "You have direct access to the user's past 7 days of nutrition/diet and physical exercises logs.\n\n"
        f"PAST 7 DAYS MEALS:\n{meals_text}\n\n"
        f"PAST 7 DAYS WORKOUTS:\n{workouts_text}\n\n"
        "Your tasks:\n"
        "1. Guide the user on what they should do today (dietary target, macronutrients intake, workout focus) based on their 7-day logs.\n"
        "2. Answer fitness and diet questions professionally.\n"
        "3. If they talk about eating a meal, analyze and log it using ICMR standard portion sizes, estimating Calories (kcal), Protein (g), Carbohydrates (g), and Fats (g).\n"
        "4. If portion size, quantity, or specific food detail is unclear to log, ask exactly ONE clarifying question.\n"
        "5. Output analysis in a clear, bulleted summary format. Keep responses brief, friendly, and structured.\n"
        "6. If you have enough details to log the meal, you MUST append a tag at the very end of your response: "
        "<meal_log>{\"description\": \"brief summary of food items\", \"calories\": 123.0, \"protein\": 12.0, \"carbs\": 34.0, \"fat\": 5.0}</meal_log>. "
        "Otherwise, do NOT append any <meal_log> tag."
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

        target_calories = 2000.0 + total_workout_calories
        target_protein = weight * 1.6
        target_carbs = weight * 3.0
        target_fat = weight * 1.0

        cal_diff = target_calories - total_meal_calories
        prot_diff = target_protein - total_protein
        carbs_diff = target_carbs - total_carbs
        fat_diff = target_fat - total_fat

        # Workout section
        workout_lines = []
        if workouts:
            workout_lines.append(f"• Active training detected: You performed {len(workouts)} sessions, burning a total of {total_workout_calories:.0f} kcal. Ensure proper muscle recovery and rest.")
        else:
            workout_lines.append("• No workouts logged today yet. Consider starting an AI-assisted Squat, Pushup, Lunge, or Curl session.")

        # Nutrition section
        nutrition_lines = []
        if cal_diff > 100:
            nutrition_lines.append(f"• Energy Demand: Consume an additional {cal_diff:.0f} kcal (Burned: {total_workout_calories:.0f} kcal, Consumed: {total_meal_calories:.0f} kcal).")
        elif cal_diff < -100:
            nutrition_lines.append(f"• Energy Surplus: You have exceeded today's targets by {abs(cal_diff):.0f} kcal. Focus on hydration.")
        else:
            nutrition_lines.append("• Energy Balance: Your daily calorie intake matches your metabolic demand.")

        if prot_diff > 5:
            nutrition_lines.append(f"• Protein Shortfall: Need {prot_diff:.0f}g more protein to support muscle synthesis.")
        else:
            nutrition_lines.append(f"• Protein Target: Met! Protein intake is sufficient ({total_protein:.0f}g).")

        if carbs_diff > 10:
            nutrition_lines.append(f"• Carbohydrate Needs: Need {carbs_diff:.0f}g more carbs to replenish glycogen stores.")
        else:
            nutrition_lines.append("• Carbohydrate Target: Met. Keep further carb intake minimal.")

        if fat_diff > 5:
            nutrition_lines.append(f"• Lipids Level: Need {fat_diff:.0f}g more healthy fats for hormonal support.")

        advice_text = (
            "[WORKOUT ADAPTATION]\n" + "\n".join(workout_lines) + "\n\n" +
            "[NUTRITION ADAPTATION]\n" + "\n".join(nutrition_lines)
        )
        
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
