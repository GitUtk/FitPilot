import React from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { NavigationContainer } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "../context/AuthContext";
import { LandingScreen } from "../screens/LandingScreen";
import { LoginScreen } from "../screens/LoginScreen";
import { SignupScreen } from "../screens/SignupScreen";
import { DashboardScreen } from "../screens/DashboardScreen";
import { PoseScreen } from "../screens/PoseScreen";
import { WorkoutLogScreen } from "../screens/WorkoutLogScreen";
import { MealLoggerScreen } from "../screens/MealLoggerScreen";
import { COLORS } from "../styles/theme";

export type RootStackParamList = {
  Landing: undefined;
  Login: undefined;
  Signup: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator();

export const AppNavigator: React.FC = () => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={COLORS.primary} />
      </View>
    );
  }

  return (
    <NavigationContainer>
      {user ? (
        <Tab.Navigator
          screenOptions={({ route }) => ({
            headerShown: false,
            tabBarActiveTintColor: "#000000",
            tabBarInactiveTintColor: COLORS.textSecondary,
            tabBarLabelStyle: {
              fontSize: 11,
              fontWeight: "600",
              paddingBottom: 4,
            },
            tabBarStyle: {
              backgroundColor: COLORS.background,
              borderTopColor: COLORS.border,
              height: 60,
              paddingTop: 6,
            },
            tabBarIcon: ({ focused, color, size }) => {
              let iconName: any;
              if (route.name === "Dashboard") {
                iconName = focused ? "home" : "home-outline";
              } else if (route.name === "Pose") {
                iconName = focused ? "camera" : "camera-outline";
              } else if (route.name === "Workouts") {
                iconName = focused ? "barbell" : "barbell-outline";
              } else if (route.name === "Food Logging") {
                iconName = focused ? "chatbubble-ellipses" : "chatbubble-ellipses-outline";
              }
              return <Ionicons name={iconName} size={20} color={focused ? "#000000" : color} />;
            },
          })}
        >
          <Tab.Screen name="Dashboard" component={DashboardScreen} />
          <Tab.Screen name="Pose" component={PoseScreen} />
          <Tab.Screen name="Workouts" component={WorkoutLogScreen} />
          <Tab.Screen name="Food Logging" component={MealLoggerScreen} />
        </Tab.Navigator>
      ) : (
        <Stack.Navigator
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: COLORS.background },
          }}
        >
          <Stack.Screen name="Landing" component={LandingScreen} />
          <Stack.Screen name="Login" component={LoginScreen} />
          <Stack.Screen name="Signup" component={SignupScreen} />
        </Stack.Navigator>
      )}
    </NavigationContainer>
  );
};

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    backgroundColor: COLORS.background,
    justifyContent: "center",
    alignItems: "center",
  },
});
