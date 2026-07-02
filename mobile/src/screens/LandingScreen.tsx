import React from "react";
import {
  StyleSheet,
  View,
  Text,
  Image,
  TouchableOpacity,
  SafeAreaView,
  Platform,
  StatusBar,
} from "react-native";
import { COLORS, SPACING, SIZES } from "../styles/theme";

export const LandingScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.logoContainer}>
          <Image
            source={require("../../assets/logo.png")}
            style={styles.logo}
            resizeMode="contain"
          />
          <Text style={styles.brandName}>FitPilot</Text>
          <Text style={styles.slogan}>Navigate your fitness. Pilot your health.</Text>
        </View>

        <View style={styles.actionContainer}>
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={() => navigation.navigate("Login")}
          >
            <Text style={styles.primaryButtonText}>Sign In</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.secondaryButton}
            onPress={() => navigation.navigate("Signup")}
          >
            <Text style={styles.secondaryButtonText}>Create an Account</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
    paddingTop: Platform.OS === "android" ? StatusBar.currentHeight : 0,
  },
  content: {
    flex: 1,
    justifyContent: "space-between",
    padding: SPACING.lg,
    maxWidth: Platform.OS === "web" ? 400 : undefined,
    width: Platform.OS === "web" ? "100%" : undefined,
    alignSelf: Platform.OS === "web" ? "center" : undefined,
  },
  logoContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    marginTop: 40,
  },
  logo: {
    width: 140,
    height: 140,
    borderRadius: 28,
    marginBottom: SPACING.lg,
  },
  brandName: {
    fontSize: 32,
    fontWeight: "bold",
    color: COLORS.textPrimary,
    letterSpacing: -1,
  },
  slogan: {
    fontSize: 14,
    color: COLORS.textSecondary,
    marginTop: SPACING.sm,
    textAlign: "center",
    maxWidth: 280,
  },
  actionContainer: {
    marginBottom: 40,
  },
  primaryButton: {
    height: SIZES.inputHeight,
    backgroundColor: COLORS.primary,
    borderRadius: SIZES.radiusSm,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: SPACING.md,
  },
  primaryButtonText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "600",
  },
  secondaryButton: {
    height: SIZES.inputHeight,
    backgroundColor: COLORS.background,
    borderRadius: SIZES.radiusSm,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  secondaryButtonText: {
    color: COLORS.textPrimary,
    fontSize: 14,
    fontWeight: "600",
  },
});
