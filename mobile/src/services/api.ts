import { Platform } from "react-native";

const BASE_URL = "https://fitpilot-backend-mc6f.onrender.com/api/v1";

export const apiService = {
  async signup(email: string, password: string, fullName?: string) {
    const response = await fetch(`${BASE_URL}/auth/signup`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email,
        password,
        full_name: fullName || null,
      }),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Signup failed");
    }
    return data;
  },

  async login(email: string, password: string) {
    const response = await fetch(`${BASE_URL}/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email,
        password,
      }),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Login failed");
    }
    return data;
  },

  async getMe(token: string) {
    const response = await fetch(`${BASE_URL}/auth/me`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to fetch user profile");
    }
    return data;
  },

  async logWorkout(token: string, exercise: string, sets: number, reps: number, weight: number) {
    const response = await fetch(`${BASE_URL}/workouts/`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        exercise,
        sets,
        reps,
        weight,
      }),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to log workout");
    }
    return data;
  },

  async getWorkouts(token: string) {
    const response = await fetch(`${BASE_URL}/workouts/`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to fetch workouts");
    }
    return data;
  },

  async getWorkoutStats(token: string) {
    const response = await fetch(`${BASE_URL}/workouts/stats`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to fetch workout stats");
    }
    return data;
  },

  async chatMeal(token: string, text: string) {
    const response = await fetch(`${BASE_URL}/meals/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        text,
      }),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to process meal chat");
    }
    return data;
  },

  async getChatHistory(token: string) {
    const response = await fetch(`${BASE_URL}/meals/chat`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to fetch chat history");
    }
    return data;
  },

  async clearChatHistory(token: string) {
    const response = await fetch(`${BASE_URL}/meals/chat`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to clear chat history");
    }
    return data;
  },

  async logMeal(token: string, description: string, calories: number, protein: number, carbs: number, fat: number) {
    const response = await fetch(`${BASE_URL}/meals/`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        description,
        calories,
        protein,
        carbs,
        fat,
      }),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to log meal");
    }
    return data;
  },

  async getMeals(token: string) {
    const response = await fetch(`${BASE_URL}/meals/`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to fetch meals");
    }
    return data;
  },

  async getAdaptationAdvice(token: string) {
    const response = await fetch(`${BASE_URL}/meals/adaptation`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.detail || "Failed to fetch adaptation advice");
    }
    return data;
  },
};
