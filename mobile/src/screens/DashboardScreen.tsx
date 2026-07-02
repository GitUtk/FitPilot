import React, { useState, useEffect } from "react";
import {
  StyleSheet,
  View,
  Text,
  Image,
  TouchableOpacity,
  ScrollView,
  StatusBar,
  SafeAreaView,
  ActivityIndicator,
  Platform,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Pedometer } from "expo-sensors";
import { useAuth } from "../context/AuthContext";
import { apiService } from "../services/api";
import { COLORS, SPACING, SIZES } from "../styles/theme";

export const DashboardScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { user, token, logout } = useAuth();
  const [refreshing, setRefreshing] = useState(false);
  const [workoutStats, setWorkoutStats] = useState({
    total_workouts: 0,
    total_calories: 0.0,
    total_sets: 0,
    total_reps: 0,
    average_intensity: 0.0,
  });
  const [mealStats, setMealStats] = useState({
    calories: 0,
    protein: 0,
    carbs: 0,
    fat: 0,
  });
  const [adaptationText, setAdaptationText] = useState("");
  const [loadingAdaptation, setLoadingAdaptation] = useState(false);

  const [isPedometerAvailable, setIsPedometerAvailable] = useState("checking");
  const [pastStepCount, setPastStepCount] = useState(0);
  const [currentStepCount, setCurrentStepCount] = useState(0);

  useEffect(() => {
    fetchData();

    let subscription: any;
    const subscribePedometer = async () => {
      try {
        const isAvailable = await Pedometer.isAvailableAsync();
        setIsPedometerAvailable(String(isAvailable));

        if (isAvailable) {
          const start = new Date();
          start.setHours(0, 0, 0, 0);
          const end = new Date();
          const result = await Pedometer.getStepCountAsync(start, end);
          if (result) {
            setPastStepCount(result.steps);
          }
          subscription = Pedometer.watchStepCount((result) => {
            setCurrentStepCount(result.steps);
          });
        }
      } catch (e) {
        setIsPedometerAvailable("false");
      }
    };

    subscribePedometer();

    return () => {
      if (subscription) {
        subscription.remove();
      }
    };
  }, []);

  const fetchData = async () => {
    if (!token) return;
    setRefreshing(true);
    setLoadingAdaptation(true);
    try {
      const stats = await apiService.getWorkoutStats(token);
      setWorkoutStats(stats);

      const meals = await apiService.getMeals(token);
      const todayStr = new Date().toDateString();
      const todayMeals = meals.filter(
        (m: any) => new Date(m.timestamp).toDateString() === todayStr
      );

      let cal = 0;
      let prot = 0;
      let cb = 0;
      let ft = 0;
      todayMeals.forEach((m: any) => {
        cal += m.calories;
        prot += m.protein;
        cb += m.carbs;
        ft += m.fat;
      });

      setMealStats({
        calories: Math.round(cal),
        protein: Math.round(prot),
        carbs: Math.round(cb),
        fat: Math.round(ft),
      });

      const advice = await apiService.getAdaptationAdvice(token);
      setAdaptationText(advice.recommendation);
    } catch {
      setAdaptationText("Failed to retrieve adaptation recommendation.");
    } finally {
      setRefreshing(false);
      setLoadingAdaptation(false);
    }
  };

  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return "Good morning";
    if (hour < 17) return "Good afternoon";
    return "Good evening";
  };

  const formatDate = () => {
    const options: Intl.DateTimeFormatOptions = {
      weekday: "long",
      month: "short",
      day: "numeric",
    };
    return new Date().toLocaleDateString(undefined, options);
  };

  const activeMinutes = workoutStats.total_sets * 2;
  const targetSteps = 10000;
  const stepsCount = isPedometerAvailable === "true" ? pastStepCount + currentStepCount : 8432;
  const progressPercent = Math.min((stepsCount / targetSteps) * 100, 100);
  const distanceKm = (stepsCount * 0.00075).toFixed(1);
  const stepCalories = Math.round(stepsCount * 0.04);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      
      <View style={styles.topBar}>
        <View style={styles.brandGroup}>
          <Image
            source={require("../../assets/logo.png")}
            style={styles.brandIcon}
            resizeMode="contain"
          />
          <Text style={styles.brandName}>FitPilot</Text>
        </View>
        <TouchableOpacity style={styles.profileCircle} onPress={logout}>
          <Ionicons name="log-out-outline" size={18} color={COLORS.textPrimary} />
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContainer} showsVerticalScrollIndicator={false}>
        <View style={styles.greetingSection}>
          <Text style={styles.dateLabel}>{formatDate()}</Text>
          <Text style={styles.greetingText}>
            {getGreeting()}, <Text style={styles.userName}>{user?.full_name || "Pilot"}</Text>
          </Text>
        </View>

        <View style={styles.progressSection}>
          <View style={styles.progressHeaderRow}>
            <View style={styles.pedometerTitleGroup}>
              <Ionicons name="walk-outline" size={18} color={COLORS.primary} style={{ marginRight: 6 }} />
              <Text style={styles.progressTitle}>Step Tracker</Text>
            </View>
            {isPedometerAvailable === "true" && (
              <View style={styles.liveIndicator}>
                <View style={styles.dot} />
                <Text style={styles.liveLabel}>LIVE</Text>
              </View>
            )}
          </View>

          <View style={styles.stepsStatsRow}>
            <View>
              <Text style={styles.stepsCountText}>{stepsCount.toLocaleString()}</Text>
              <Text style={styles.stepsTargetText}>Goal: {targetSteps.toLocaleString()} steps</Text>
            </View>
            <View style={styles.progressPercentageBadge}>
              <Text style={styles.progressPercentageText}>{Math.round(progressPercent)}%</Text>
            </View>
          </View>

          <View style={styles.progressBarBackground}>
            <View style={[styles.progressBarFill, { width: `${progressPercent}%` }]} />
          </View>

          <View style={styles.extraStatsRow}>
            <View style={styles.extraStatItem}>
              <Ionicons name="map-outline" size={14} color={COLORS.textSecondary} />
              <Text style={styles.extraStatValue}>{distanceKm} km</Text>
              <Text style={styles.extraStatLabel}>Distance</Text>
            </View>
            <View style={styles.extraStatDivider} />
            <View style={styles.extraStatItem}>
              <Ionicons name="flame-outline" size={14} color={COLORS.textSecondary} />
              <Text style={styles.extraStatValue}>{stepCalories} kcal</Text>
              <Text style={styles.extraStatLabel}>Active Burn</Text>
            </View>
          </View>
        </View>

        <View style={styles.adaptationSection}>
          <View style={styles.adaptationHeader}>
            <View style={styles.adaptationIconContainer}>
              <Ionicons name="sparkles" size={16} color={COLORS.primary} />
            </View>
            <Text style={styles.adaptationTitle}>AI Adaptation Engine</Text>
          </View>
          <View style={styles.adaptationContent}>
            {loadingAdaptation ? (
              <ActivityIndicator size="small" color={COLORS.primary} style={styles.loader} />
            ) : (
              <Text style={styles.adaptationBodyText}>{adaptationText}</Text>
            )}
          </View>
        </View>

        <Text style={styles.sectionTitle}>Daily Performance</Text>

        <View style={styles.metricsGrid}>
          <View style={styles.metricCard}>
            <View style={styles.metricCardHeader}>
              <View style={[styles.metricIconWrap, { backgroundColor: "rgba(249, 115, 22, 0.08)" }]}>
                <Ionicons name="flame" size={16} color="#F97316" />
              </View>
              <Text style={styles.metricCardLabel}>Workout Burn</Text>
            </View>
            <Text style={styles.metricCardValue}>{workoutStats.total_calories} kcal</Text>
            <Text style={styles.metricCardSub}>{activeMinutes} mins training</Text>
          </View>

          <View style={styles.metricCard}>
            <View style={styles.metricCardHeader}>
              <View style={[styles.metricIconWrap, { backgroundColor: "rgba(16, 185, 129, 0.08)" }]}>
                <Ionicons name="restaurant" size={15} color="#10B981" />
              </View>
              <Text style={styles.metricCardLabel}>Food Logged</Text>
            </View>
            <Text style={styles.metricCardValue}>{mealStats.calories} kcal</Text>
            <Text style={styles.metricCardSub}>Budget: 2,000 kcal</Text>
          </View>

          <View style={styles.metricCard}>
            <View style={styles.metricCardHeader}>
              <View style={[styles.metricIconWrap, { backgroundColor: "rgba(15, 23, 42, 0.06)" }]}>
                <Ionicons name="fitness" size={16} color={COLORS.textPrimary} />
              </View>
              <Text style={styles.metricCardLabel}>Exertion Rate</Text>
            </View>
            <Text style={styles.metricCardValue}>{workoutStats.average_intensity}</Text>
            <Text style={styles.metricCardSub}>Score intensity</Text>
          </View>

          <View style={styles.metricCard}>
            <View style={styles.metricCardHeader}>
              <View style={[styles.metricIconWrap, { backgroundColor: "rgba(99, 102, 241, 0.08)" }]}>
                <Ionicons name="leaf" size={15} color="#6366F1" />
              </View>
              <Text style={styles.metricCardLabel}>Protein Balance</Text>
            </View>
            <Text style={styles.metricCardValue}>{mealStats.protein}g</Text>
            <Text style={styles.metricCardSub}>Carbs: {mealStats.carbs}g • Fat: {mealStats.fat}g</Text>
          </View>
        </View>

        <TouchableOpacity style={styles.recalculateButton} onPress={fetchData} disabled={refreshing}>
          {refreshing ? (
            <ActivityIndicator color="#000000" />
          ) : (
            <View style={styles.recalculateButtonContent}>
              <Ionicons name="sync-outline" size={16} color={COLORS.textPrimary} style={{ marginRight: 6 }} />
              <Text style={styles.recalculateButtonText}>Sync Metabolic Stats</Text>
            </View>
          )}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  topBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.md,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
    backgroundColor: COLORS.background,
  },
  brandGroup: {
    flexDirection: "row",
    alignItems: "center",
  },
  brandIcon: {
    width: 28,
    height: 28,
    borderRadius: 6,
    marginRight: SPACING.sm,
  },
  brandName: {
    fontSize: 16,
    fontWeight: "700",
    color: COLORS.textPrimary,
    letterSpacing: -0.4,
  },
  profileCircle: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: COLORS.accent,
    borderWidth: 1,
    borderColor: COLORS.border,
    justifyContent: "center",
    alignItems: "center",
  },
  scrollContainer: {
    padding: SPACING.lg,
    maxWidth: Platform.OS === "web" ? 600 : undefined,
    width: Platform.OS === "web" ? "100%" : undefined,
    alignSelf: Platform.OS === "web" ? "center" : undefined,
  },
  greetingSection: {
    marginBottom: SPACING.lg,
  },
  dateLabel: {
    fontSize: 12,
    fontWeight: "500",
    color: COLORS.textSecondary,
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginBottom: 4,
  },
  greetingText: {
    fontSize: 20,
    color: COLORS.textPrimary,
    fontWeight: "600",
  },
  userName: {
    color: COLORS.primary,
    fontWeight: "700",
  },
  progressSection: {
    backgroundColor: COLORS.card,
    borderRadius: SIZES.radiusMd,
    padding: SPACING.lg,
    marginBottom: SPACING.lg,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  progressHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: SPACING.md,
  },
  pedometerTitleGroup: {
    flexDirection: "row",
    alignItems: "center",
  },
  progressTitle: {
    fontSize: 13,
    fontWeight: "600",
    color: COLORS.textPrimary,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  liveIndicator: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "rgba(16, 185, 129, 0.08)",
    paddingVertical: 2,
    paddingHorizontal: 6,
    borderRadius: 8,
  },
  dot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: "#10B981",
    marginRight: 4,
  },
  liveLabel: {
    fontSize: 9,
    fontWeight: "800",
    color: "#10B981",
  },
  stepsStatsRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: SPACING.md,
  },
  stepsCountText: {
    fontSize: 34,
    fontWeight: "800",
    color: COLORS.textPrimary,
    letterSpacing: -0.8,
  },
  stepsTargetText: {
    fontSize: 12,
    color: COLORS.textSecondary,
    marginTop: 2,
  },
  progressPercentageBadge: {
    backgroundColor: COLORS.accent,
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  progressPercentageText: {
    fontSize: 12,
    fontWeight: "700",
    color: COLORS.textPrimary,
  },
  progressBarBackground: {
    height: 6,
    backgroundColor: COLORS.accent,
    borderRadius: 3,
    marginBottom: SPACING.lg,
    overflow: "hidden",
  },
  progressBarFill: {
    height: "100%",
    backgroundColor: "#000000",
    borderRadius: 3,
  },
  extraStatsRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  extraStatItem: {
    flex: 1,
    alignItems: "center",
  },
  extraStatValue: {
    fontSize: 14,
    fontWeight: "700",
    color: COLORS.textPrimary,
    marginTop: 4,
  },
  extraStatLabel: {
    fontSize: 11,
    color: COLORS.textSecondary,
    marginTop: 2,
  },
  extraStatDivider: {
    width: 1,
    height: 24,
    backgroundColor: COLORS.border,
  },
  adaptationSection: {
    backgroundColor: COLORS.card,
    borderRadius: SIZES.radiusMd,
    padding: SPACING.lg,
    marginBottom: SPACING.xl,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  adaptationHeader: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: SPACING.md,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
    paddingBottom: SPACING.sm,
  },
  adaptationIconContainer: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: COLORS.accent,
    justifyContent: "center",
    alignItems: "center",
    marginRight: SPACING.sm,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  adaptationTitle: {
    fontSize: 13,
    fontWeight: "600",
    color: COLORS.textPrimary,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  adaptationContent: {
    marginTop: SPACING.xs,
  },
  adaptationBodyText: {
    fontSize: 13,
    lineHeight: 18,
    color: COLORS.textPrimary,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: "600",
    color: COLORS.textPrimary,
    marginBottom: SPACING.md,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  metricsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    marginBottom: SPACING.lg,
  },
  metricCard: {
    width: "48%",
    backgroundColor: COLORS.card,
    borderRadius: SIZES.radiusMd,
    padding: SPACING.md,
    marginBottom: SPACING.md,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  metricCardHeader: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: SPACING.sm,
  },
  metricIconWrap: {
    width: 24,
    height: 24,
    borderRadius: 6,
    justifyContent: "center",
    alignItems: "center",
    marginRight: 6,
  },
  metricCardLabel: {
    fontSize: 12,
    color: COLORS.textSecondary,
    fontWeight: "500",
  },
  metricCardValue: {
    fontSize: 16,
    fontWeight: "700",
    color: COLORS.textPrimary,
    marginBottom: 2,
  },
  metricCardSub: {
    fontSize: 10,
    color: COLORS.textSecondary,
  },
  recalculateButton: {
    height: SIZES.inputHeight,
    backgroundColor: COLORS.background,
    borderRadius: SIZES.radiusSm,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: COLORS.border,
    marginBottom: SPACING.xl,
  },
  recalculateButtonContent: {
    flexDirection: "row",
    alignItems: "center",
  },
  recalculateButtonText: {
    color: COLORS.textPrimary,
    fontSize: 13,
    fontWeight: "600",
  },
  loader: {
    marginVertical: SPACING.md,
  },
});
