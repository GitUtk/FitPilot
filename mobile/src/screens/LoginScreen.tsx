import React, { useState } from "react";
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  Image,
  TouchableOpacity,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  TouchableWithoutFeedback,
  Keyboard,
  StatusBar,
} from "react-native";
import { useAuth } from "../context/AuthContext";
import { COLORS, SPACING, SIZES } from "../styles/theme";

export const LoginScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = async () => {
    if (!email || !password) {
      setError("Please fill in all fields");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await login(email.trim(), password);
    } catch (err: any) {
      setError(err.message || "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const content = (
    <View style={styles.inner}>
      <View style={styles.header}>
        <Image
          source={require("../../assets/logo.png")}
          style={styles.headerLogo}
          resizeMode="contain"
        />
        <Text style={styles.logo}>FitPilot</Text>
        <Text style={styles.subtitle}>Enter your email below to login to your account</Text>
      </View>

      <View style={styles.form}>
        {error && (
          <View style={styles.errorContainer}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        <View style={styles.inputContainer}>
          <Text style={styles.label}>Email</Text>
          <TextInput
            style={styles.input}
            placeholder="name@example.com"
            placeholderTextColor={COLORS.textSecondary}
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
          />
        </View>

        <View style={styles.inputContainer}>
          <Text style={styles.label}>Password</Text>
          <TextInput
            style={styles.input}
            placeholder="Enter your password"
            placeholderTextColor={COLORS.textSecondary}
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoCapitalize="none"
          />
        </View>

        <TouchableOpacity
          style={styles.button}
          onPress={handleLogin}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#FFFFFF" />
          ) : (
            <Text style={styles.buttonText}>Sign In with Email</Text>
          )}
        </TouchableOpacity>
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerText}>Don't have an account? </Text>
        <TouchableOpacity onPress={() => navigation.navigate("Signup")}>
          <Text style={styles.footerLink}>Sign Up</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      style={styles.container}
    >
      {Platform.OS === "web" ? (
        content
      ) : (
        <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
          {content}
        </TouchableWithoutFeedback>
      )}
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
    paddingTop: Platform.OS === "android" ? StatusBar.currentHeight : 0,
  },
  inner: {
    flex: 1,
    justifyContent: "center",
    padding: SPACING.lg,
    maxWidth: Platform.OS === "web" ? 400 : undefined,
    width: Platform.OS === "web" ? "100%" : undefined,
    alignSelf: Platform.OS === "web" ? "center" : undefined,
  },
  header: {
    alignItems: "center",
    marginBottom: SPACING.lg,
  },
  headerLogo: {
    width: 64,
    height: 64,
    borderRadius: SIZES.radiusSm + 4,
    marginBottom: SPACING.md,
  },
  logo: {
    fontSize: 28,
    fontWeight: "bold",
    color: COLORS.textPrimary,
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 14,
    color: COLORS.textSecondary,
    marginTop: SPACING.xs,
    textAlign: "center",
  },
  form: {
    backgroundColor: COLORS.background,
    borderRadius: SIZES.radiusMd,
    padding: SPACING.md,
  },
  errorContainer: {
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
  inputContainer: {
    marginBottom: SPACING.md,
  },
  label: {
    fontSize: 14,
    fontWeight: "500",
    color: COLORS.textPrimary,
    marginBottom: SPACING.xs,
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
  },
  button: {
    height: SIZES.inputHeight,
    backgroundColor: COLORS.primary,
    borderRadius: SIZES.radiusSm,
    justifyContent: "center",
    alignItems: "center",
    marginTop: SPACING.sm,
  },
  buttonText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "600",
  },
  footer: {
    flexDirection: "row",
    justifyContent: "center",
    marginTop: SPACING.lg,
  },
  footerText: {
    color: COLORS.textSecondary,
    fontSize: 13,
  },
  footerLink: {
    color: COLORS.textPrimary,
    fontSize: 13,
    fontWeight: "600",
    textDecorationLine: "underline",
  },
});
