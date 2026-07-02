import React, { useState, useEffect } from "react";
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  SafeAreaView,
  Platform,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "../context/AuthContext";
import { apiService } from "../services/api";
import { COLORS, SPACING, SIZES } from "../styles/theme";

interface Workout {
  id: string;
  exercise: string;
  sets: number;
  reps: number;
  weight: number;
  duration_minutes: number;
  calories_burned: number;
  intensity_score: number;
  timestamp: string;
}

export const WorkoutLogScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { token, logout } = useAuth();
  const [exercise, setExercise] = useState<"Squat" | "Curl">("Squat");
  const [sets, setSets] = useState("3");
  const [reps, setReps] = useState("10");
  const [weight, setWeight] = useState("40");
  const [workouts, setWorkouts] = useState<Workout[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchWorkouts();
  }, []);

  const fetchWorkouts = async () => {
    if (!token) return;
    setLoading(true);
    try {
      const data = await apiService.getWorkouts(token);
      setWorkouts(data);
    } catch (err: any) {
      setError(err.message || "Failed to load workouts");
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!token) return;
    const s = parseInt(sets);
    const r = parseInt(reps);
    const w = parseFloat(weight);

    if (isNaN(s) || s <= 0 || isNaN(r) || r <= 0 || isNaN(w) || w < 0) {
      setError("Please enter valid positive numbers");
      return;
    }

    setError(null);
    setSaving(true);
    try {
      const newWorkout = await apiService.logWorkout(token, exercise, s, r, w);
      setWorkouts((prev) => [newWorkout, ...prev]);
      setSets("3");
      setReps("10");
      setWeight("40");
    } catch (err: any) {
      setError(err.message || "Failed to save workout log");
    } finally {
      setSaving(false);
    }
  };

  const formatDate = (dateString: string) => {
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString(undefined, {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return dateString;
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <View style={styles.placeholder} />
        <Text style={styles.headerTitle}>Workout Log</Text>
        <TouchableOpacity onPress={logout} style={styles.logoutBtn}>
          <Ionicons name="log-out-outline" size={18} color={COLORS.textPrimary} />
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContainer} showsVerticalScrollIndicator={false}>
        <View style={styles.formCard}>
          {error && (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          <Text style={styles.label}>Exercise Type</Text>
          <View style={styles.toggleRow}>
            <TouchableOpacity
              style={[styles.toggleBtn, exercise === "Squat" && styles.toggleBtnActive]}
              onPress={() => setExercise("Squat")}
            >
              <Text style={[styles.toggleBtnText, exercise === "Squat" && styles.toggleBtnTextActive]}>
                Squat
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.toggleBtn, exercise === "Curl" && styles.toggleBtnActive]}
              onPress={() => setExercise("Curl")}
            >
              <Text style={[styles.toggleBtnText, exercise === "Curl" && styles.toggleBtnTextActive]}>
                Bicep Curl
              </Text>
            </TouchableOpacity>
          </View>

          <View style={styles.row}>
            <View style={styles.col}>
              <Text style={styles.label}>Sets</Text>
              <TextInput
                style={styles.input}
                value={sets}
                onChangeText={setSets}
                keyboardType="number-pad"
                placeholder="3"
              />
            </View>
            <View style={styles.col}>
              <Text style={styles.label}>Reps</Text>
              <TextInput
                style={styles.input}
                value={reps}
                onChangeText={setReps}
                keyboardType="number-pad"
                placeholder="10"
              />
            </View>
          </View>

          <Text style={styles.label}>Weight (kg)</Text>
          <TextInput
            style={styles.input}
            value={weight}
            onChangeText={setWeight}
            keyboardType="decimal-pad"
            placeholder="40"
          />

          <TouchableOpacity style={styles.saveBtn} onPress={handleSave} disabled={saving}>
            {saving ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.saveBtnText}>Save Workout Log</Text>
            )}
          </TouchableOpacity>
        </View>

        <Text style={styles.sectionTitle}>Recent Workout Activity</Text>

        {loading ? (
          <ActivityIndicator size="small" color={COLORS.primary} style={styles.loader} />
        ) : workouts.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyText}>No workout logs recorded yet</Text>
          </View>
        ) : (
          workouts.map((w) => (
            <View key={w.id} style={styles.logCard}>
              <View style={styles.logHeader}>
                <View style={styles.logInfo}>
                  <Text style={styles.logTitle}>{w.exercise}</Text>
                  <Text style={styles.logDate}>{formatDate(w.timestamp)}</Text>
                </View>
                <Text style={styles.logDetail}>
                  {w.sets} sets × {w.reps} reps @ {w.weight}kg
                </Text>
              </View>

              <View style={styles.logMetrics}>
                <View style={styles.metricItem}>
                  <Ionicons name="flame-outline" size={13} color="#F97316" />
                  <Text style={styles.metricLabel}>{w.calories_burned} kcal</Text>
                </View>
                <View style={styles.metricItem}>
                  <Ionicons name="flash-outline" size={13} color={COLORS.success} />
                  <Text style={styles.metricLabel}>Intensity: {w.intensity_score}</Text>
                </View>
                <View style={styles.metricItem}>
                  <Ionicons name="time-outline" size={13} color={COLORS.textSecondary} />
                  <Text style={styles.metricLabel}>{w.duration_minutes} min</Text>
                </View>
              </View>
            </View>
          ))
        )}
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
    backgroundColor: COLORS.background,
  },
  logoutBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: COLORS.background,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: COLORS.textPrimary,
  },
  placeholder: {
    width: 36,
  },
  scrollContainer: {
    padding: SPACING.lg,
    maxWidth: Platform.OS === "web" ? 500 : undefined,
    width: Platform.OS === "web" ? "100%" : undefined,
    alignSelf: Platform.OS === "web" ? "center" : undefined,
  },
  formCard: {
    backgroundColor: COLORS.card,
    borderRadius: SIZES.radiusMd,
    padding: SPACING.lg,
    borderWidth: 1,
    borderColor: COLORS.border,
    marginBottom: SPACING.xl,
  },
  errorBox: {
    backgroundColor: "rgba(239, 68, 68, 0.05)",
    borderWidth: 1,
    borderColor: COLORS.error,
    borderRadius: SIZES.radiusSm,
    padding: SPACING.sm,
    marginBottom: SPACING.md,
  },
  errorText: {
    color: COLORS.error,
    fontSize: 13,
    textAlign: "center",
  },
  label: {
    fontSize: 14,
    fontWeight: "500",
    color: COLORS.textPrimary,
    marginBottom: SPACING.xs,
  },
  toggleRow: {
    flexDirection: "row",
    backgroundColor: COLORS.accent,
    borderRadius: SIZES.radiusSm,
    padding: 2,
    marginBottom: SPACING.md,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  toggleBtn: {
    flex: 1,
    paddingVertical: SPACING.sm - 2,
    alignItems: "center",
    borderRadius: SIZES.radiusSm - 2,
  },
  toggleBtnActive: {
    backgroundColor: COLORS.background,
  },
  toggleBtnText: {
    color: COLORS.textSecondary,
    fontSize: 13,
    fontWeight: "500",
  },
  toggleBtnTextActive: {
    color: COLORS.textPrimary,
    fontWeight: "600",
  },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: SPACING.md,
  },
  col: {
    width: "48%",
  },
  input: {
    height: SIZES.inputHeight,
    backgroundColor: COLORS.background,
    borderRadius: SIZES.radiusSm,
    borderColor: COLORS.border,
    borderWidth: 1,
    paddingHorizontal: SPACING.sm,
    color: COLORS.textPrimary,
    fontSize: 14,
    marginBottom: SPACING.md,
  },
  saveBtn: {
    height: SIZES.inputHeight,
    backgroundColor: COLORS.primary,
    borderRadius: SIZES.radiusSm,
    justifyContent: "center",
    alignItems: "center",
    marginTop: SPACING.xs,
  },
  saveBtnText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "600",
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: COLORS.textPrimary,
    marginBottom: SPACING.md,
    letterSpacing: -0.2,
  },
  loader: {
    marginVertical: SPACING.xl,
  },
  emptyCard: {
    padding: SPACING.xl,
    backgroundColor: COLORS.card,
    borderRadius: SIZES.radiusMd,
    borderWidth: 1,
    borderColor: COLORS.border,
    alignItems: "center",
  },
  emptyText: {
    color: COLORS.textSecondary,
    fontSize: 13,
    fontStyle: "italic",
  },
  logCard: {
    backgroundColor: COLORS.card,
    borderRadius: SIZES.radiusMd,
    padding: SPACING.md,
    marginBottom: SPACING.md,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  logHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
    paddingBottom: SPACING.sm,
    marginBottom: SPACING.sm,
  },
  logInfo: {
    flex: 1,
  },
  logTitle: {
    fontSize: 14,
    fontWeight: "600",
    color: COLORS.textPrimary,
  },
  logDate: {
    fontSize: 11,
    color: COLORS.textSecondary,
    marginTop: 2,
  },
  logDetail: {
    fontSize: 13,
    color: COLORS.textPrimary,
    fontWeight: "500",
  },
  logMetrics: {
    flexDirection: "row",
    alignItems: "center",
  },
  metricItem: {
    flexDirection: "row",
    alignItems: "center",
    marginRight: SPACING.md,
  },
  metricLabel: {
    fontSize: 12,
    color: COLORS.textSecondary,
    marginLeft: 4,
    fontWeight: "500",
  },
});
