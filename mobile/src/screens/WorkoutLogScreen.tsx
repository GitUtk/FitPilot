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
  StatusBar,
  Image,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "../context/AuthContext";
import { apiService } from "../services/api";

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

interface ExerciseConfig {
  key: string;
  name: string;
  image: any;
  aiAvailable: boolean;
  defaultWeight: string;
  defaultReps: string;
}

export const WorkoutLogScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { token } = useAuth();
  const [activeEx, setActiveEx] = useState<string | null>(null);
  const [showInput, setShowInput] = useState(false);
  const [sets, setSets] = useState("3");
  const [reps, setReps] = useState("10");
  const [weight, setWeight] = useState("40");
  const [workouts, setWorkouts] = useState<Workout[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const EXERCISES: ExerciseConfig[] = [
    {
      key: "Squat",
      name: "Squats",
      image: require("../../assets/squat.png"),
      aiAvailable: true,
      defaultWeight: "40",
      defaultReps: "10",
    },
    {
      key: "Curl",
      name: "Bicep Curls",
      image: require("../../assets/curl.png"),
      aiAvailable: true,
      defaultWeight: "15",
      defaultReps: "12",
    },
    {
      key: "Pushup",
      name: "Push-Ups",
      image: require("../../assets/pushup.png"),
      aiAvailable: false,
      defaultWeight: "0",
      defaultReps: "15",
    },
    {
      key: "Lunge",
      name: "Lunges",
      image: require("../../assets/lunge.png"),
      aiAvailable: false,
      defaultWeight: "20",
      defaultReps: "12",
    },
    {
      key: "Press",
      name: "Overhead Press",
      image: require("../../assets/press.png"),
      aiAvailable: false,
      defaultWeight: "30",
      defaultReps: "10",
    },
  ];

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

  const handleSelect = (key: string) => {
    if (activeEx === key) {
      setShowInput(!showInput);
    } else {
      setActiveEx(key);
      setShowInput(true);
      const ex = EXERCISES.find((e) => e.key === key);
      if (ex) {
        setWeight(ex.defaultWeight);
        setReps(ex.defaultReps);
        setSets("3");
      }
    }
  };

  const handleSave = async () => {
    if (!token || !activeEx) return;
    const s = parseInt(sets);
    const r = parseInt(reps);
    const w = parseFloat(weight);

    const activeConfig = EXERCISES.find((e) => e.key === activeEx);
    if (!activeConfig) return;

    if (isNaN(s) || s <= 0 || isNaN(r) || r <= 0 || isNaN(w) || w < 0) {
      setError("Please enter valid positive numbers");
      return;
    }

    setError(null);
    setSaving(true);
    try {
      const newWorkout = await apiService.logWorkout(token, activeConfig.name, s, r, w);
      setWorkouts((prev) => [newWorkout, ...prev]);
      setShowInput(false);
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
        <Text style={styles.headerTitle}>Workout Log</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContainer} showsVerticalScrollIndicator={false}>
        {/* 5 Cards Vertically */}
        <View style={styles.exerciseList}>
          {EXERCISES.map((ex) => {
            const isSelected = activeEx === ex.key && showInput;
            return (
              <View key={ex.key} style={styles.exerciseContainer}>
                {/* Horizontal Card Row */}
                <TouchableOpacity
                  style={[styles.exerciseCard, isSelected && styles.exerciseCardActive]}
                  onPress={() => handleSelect(ex.key)}
                >
                  <View style={styles.cardLeft}>
                    <View style={styles.iconContainer}>
                      <Image source={ex.image} style={styles.silhouetteIcon} resizeMode="contain" />
                    </View>
                    <Text style={styles.exerciseName}>{ex.name}</Text>
                  </View>
                  <Ionicons
                    name={isSelected ? "chevron-up" : "chevron-down"}
                    size={18}
                    color="#71717A"
                  />
                </TouchableOpacity>

                {/* Local Collapsible Dropdown Panel */}
                {isSelected && (
                  <View style={styles.dropdownPanel}>
                    {error && <Text style={styles.errorText}>{error}</Text>}

                    <View style={styles.inputRow}>
                      <View style={styles.inputGroup}>
                        <Text style={styles.label}>Sets</Text>
                        <TextInput
                          style={styles.input}
                          value={sets}
                          onChangeText={setSets}
                          keyboardType="number-pad"
                        />
                      </View>
                      <View style={styles.inputGroup}>
                        <Text style={styles.label}>Reps</Text>
                        <TextInput
                          style={styles.input}
                          value={reps}
                          onChangeText={setReps}
                          keyboardType="number-pad"
                        />
                      </View>
                      <View style={styles.inputGroup}>
                        <Text style={styles.label}>Weight (kg)</Text>
                        <TextInput
                          style={styles.input}
                          value={weight}
                          onChangeText={setWeight}
                          keyboardType="decimal-pad"
                        />
                      </View>
                    </View>

                    <TouchableOpacity style={styles.saveBtn} onPress={handleSave} disabled={saving}>
                      {saving ? (
                        <ActivityIndicator color="#FFFFFF" />
                      ) : (
                        <Text style={styles.saveBtnText}>Log Set</Text>
                      )}
                    </TouchableOpacity>

                    {/* AI Camera Link if available */}
                    {ex.aiAvailable && (
                      <TouchableOpacity
                        style={styles.aiLink}
                        onPress={() =>
                          navigation.navigate("Pose", { mode: ex.key === "Squat" ? "squat" : "curl" })
                        }
                      >
                        <Ionicons name="scan-outline" size={14} color="#09090B" />
                        <Text style={styles.aiLinkText}>Start Real-Time AI Scan</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                )}
              </View>
            );
          })}
        </View>

        {/* History Log list */}
        <Text style={styles.sectionTitle}>History</Text>

        {loading ? (
          <ActivityIndicator size="small" color="#09090B" style={styles.loader} />
        ) : workouts.length === 0 ? (
          <Text style={styles.emptyText}>No logs found</Text>
        ) : (
          workouts.map((w) => (
            <View key={w.id} style={styles.logCard}>
              <View style={styles.logHeader}>
                <Text style={styles.logTitle}>{w.exercise}</Text>
                <Text style={styles.logDetail}>
                  {w.sets} sets × {w.reps} reps @ {w.weight}kg
                </Text>
              </View>
              <Text style={styles.logDate}>{formatDate(w.timestamp)}</Text>
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
    backgroundColor: "#FFFFFF",
    paddingTop: Platform.OS === "android" ? StatusBar.currentHeight : 0,
  },
  header: {
    paddingHorizontal: 20,
    paddingVertical: 15,
    borderBottomWidth: 1,
    borderBottomColor: "#F4F4F5",
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: "#09090B",
    letterSpacing: -0.5,
  },
  scrollContainer: {
    padding: 20,
    maxWidth: Platform.OS === "web" ? 480 : undefined,
    alignSelf: Platform.OS === "web" ? "center" : undefined,
    width: "100%",
  },
  exerciseList: {
    marginBottom: 24,
  },
  exerciseContainer: {
    marginBottom: 16,
    borderWidth: 1,
    borderColor: "#E4E4E7",
    borderRadius: 8,
    backgroundColor: "#FFFFFF",
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.02,
    shadowRadius: 2,
    elevation: 1,
  },
  exerciseCard: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 16,
    backgroundColor: "#FFFFFF",
  },
  exerciseCardActive: {
    backgroundColor: "#FAFAFA",
    borderBottomWidth: 1,
    borderBottomColor: "#E4E4E7",
  },
  cardLeft: {
    flexDirection: "row",
    alignItems: "center",
  },
  iconContainer: {
    width: 48,
    height: 48,
    borderRadius: 6, // Square/rounded box, not a circle
    backgroundColor: "#F4F4F5",
    justifyContent: "center",
    alignItems: "center",
    marginRight: 16,
  },
  silhouetteIcon: {
    width: 48,
    height: 48,
  },
  exerciseName: {
    fontSize: 16,
    fontWeight: "600",
    color: "#09090B",
  },
  dropdownPanel: {
    padding: 20,
    backgroundColor: "#FFFFFF",
  },
  errorText: {
    color: "#EF4444",
    fontSize: 12,
    marginBottom: 12,
  },
  inputRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 16,
  },
  inputGroup: {
    flex: 1,
    marginHorizontal: 4,
  },
  label: {
    fontSize: 11,
    fontWeight: "500",
    color: "#71717A",
    marginBottom: 6,
    textTransform: "uppercase",
    letterSpacing: 0.3,
  },
  input: {
    height: 44,
    borderWidth: 1,
    borderColor: "#E4E4E7",
    borderRadius: 6,
    paddingHorizontal: 12,
    fontSize: 14,
    color: "#09090B",
    backgroundColor: "#FAFAFA",
  },
  saveBtn: {
    backgroundColor: "#09090B",
    height: 44,
    borderRadius: 6,
    justifyContent: "center",
    alignItems: "center",
  },
  saveBtnText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "600",
  },
  aiLink: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 12,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: "#E4E4E7",
    borderRadius: 6,
    backgroundColor: "#FFFFFF",
  },
  aiLinkText: {
    fontSize: 12,
    color: "#09090B",
    marginLeft: 6,
    fontWeight: "600",
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: "#09090B",
    marginBottom: 12,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  loader: {
    marginVertical: 20,
  },
  emptyText: {
    color: "#71717A",
    fontSize: 13,
    fontStyle: "italic",
  },
  logCard: {
    backgroundColor: "#FFFFFF",
    borderWidth: 1,
    borderColor: "#E4E4E7",
    borderRadius: 6,
    padding: 12,
    marginBottom: 8,
  },
  logHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  logTitle: {
    fontSize: 13,
    fontWeight: "600",
    color: "#09090B",
  },
  logDetail: {
    fontSize: 12,
    fontWeight: "500",
    color: "#27272A",
  },
  logDate: {
    fontSize: 10,
    color: "#71717A",
    marginTop: 4,
  },
});
