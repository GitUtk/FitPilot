import React, { useState, useEffect, useRef } from "react";
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  SafeAreaView,
  StatusBar,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "../context/AuthContext";
import { apiService } from "../services/api";
import { COLORS, SPACING, SIZES } from "../styles/theme";

interface Message {
  id: string;
  role: "user" | "model";
  text: string;
  timestamp: Date;
}

export const MealLoggerScreen: React.FC = () => {
  const { token } = useAuth();
  const [messages, setMessages] = useState<Message[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [inputText, setInputText] = useState("");
  const [sending, setSending] = useState(false);
  const flatListRef = useRef<FlatList>(null);

  const [showLogForm, setShowLogForm] = useState(false);
  const [logDesc, setLogDesc] = useState("");
  const [logCalories, setLogCalories] = useState("");
  const [logProtein, setLogProtein] = useState("");
  const [logCarbs, setLogCarbs] = useState("");
  const [logFat, setLogFat] = useState("");
  const [loggingMeal, setLoggingMeal] = useState(false);
  const [logError, setLogError] = useState<string | null>(null);
  const [logSuccess, setLogSuccess] = useState(false);

  useEffect(() => {
    loadChatHistory();
  }, []);

  const loadChatHistory = async () => {
    if (!token) return;
    setLoadingHistory(true);
    try {
      const history = await apiService.getChatHistory(token);
      if (history && history.length > 0) {
        const formatted = history.map((h: any) => ({
          id: h.id,
          role: h.role,
          text: h.text,
          timestamp: new Date(h.timestamp),
        }));
        setMessages(formatted);
      } else {
        setMessages([
          {
            id: "welcome",
            role: "model",
            text: "Hello! I am your nutrition assistant. Tell me what you ate in plain language (e.g. 'I had 2 medium rotis and a bowl of yellow dal for lunch') and I'll calculate your macros using ICMR standards.",
            timestamp: new Date(),
          },
        ]);
      }
    } catch (e) {
      console.log("Error loading chat history:", e);
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleSend = async () => {
    if (!token || !inputText.trim() || sending) return;
    const textToSend = inputText.trim();
    setInputText("");

    const userMessage: Message = {
      id: Math.random().toString(),
      role: "user",
      text: textToSend,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setSending(true);

    try {
      const response = await apiService.chatMeal(token, textToSend);
      
      const modelMessage: Message = {
        id: response.id || Math.random().toString(),
        role: response.role || "model",
        text: response.text,
        timestamp: response.timestamp ? new Date(response.timestamp) : new Date(),
      };
      setMessages((prev) => [...prev, modelMessage]);
    } catch (err: any) {
      const errorMessage: Message = {
        id: Math.random().toString(),
        role: "model",
        text: err.message || "Sorry, I encountered an issue. Please try again.",
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setSending(false);
      setTimeout(() => {
        flatListRef.current?.scrollToEnd({ animated: true });
      }, 100);
    }
  };

  const handleLogMealSubmit = async () => {
    if (!token) return;
    const calories = parseFloat(logCalories);
    const protein = parseFloat(logProtein);
    const carbs = parseFloat(logCarbs);
    const fat = parseFloat(logFat);

    if (!logDesc.trim() || isNaN(calories) || calories < 0) {
      setLogError("Please enter description and valid calories");
      return;
    }

    setLogError(null);
    setLoggingMeal(true);
    try {
      await apiService.logMeal(
        token,
        logDesc.trim(),
        calories,
        isNaN(protein) ? 0 : protein,
        isNaN(carbs) ? 0 : carbs,
        isNaN(fat) ? 0 : fat
      );
      setLogSuccess(true);
      setLogDesc("");
      setLogCalories("");
      setLogProtein("");
      setLogCarbs("");
      setLogFat("");
      setTimeout(() => setLogSuccess(false), 2000);
    } catch (err: any) {
      setLogError(err.message || "Failed to log meal to DB");
    } finally {
      setLoggingMeal(false);
    }
  };

  const clearChat = async () => {
    if (!token) return;
    try {
      await apiService.clearChatHistory(token);
      setMessages([
        {
          id: "welcome",
          role: "model",
          text: "Hello! I am your nutrition assistant. Tell me what you ate in plain language (e.g. 'I had 2 medium rotis and a bowl of yellow dal for lunch') and I'll calculate your macros using ICMR standards.",
          timestamp: new Date(),
        },
      ]);
    } catch (e) {
      console.log("Failed to clear chat:", e);
    }
  };

  const renderFormattedText = (text: string, isUser: boolean) => {
    const lines = text.split("\n");
    return lines.map((line, idx) => {
      let isBullet = false;
      let indentLevel = 0;
      let cleanLine = line;

      const subBulletMatch = line.match(/^(\s+)[*-]\s+(.*)/);
      const bulletMatch = line.match(/^[*-]\s+(.*)/);

      if (subBulletMatch) {
        isBullet = true;
        indentLevel = subBulletMatch[1].length;
        cleanLine = subBulletMatch[2];
      } else if (bulletMatch) {
        isBullet = true;
        cleanLine = bulletMatch[1];
      }

      const parseBoldText = (str: string) => {
        const parts = str.split("**");
        return parts.map((part, pIdx) => {
          if (pIdx % 2 !== 0) {
            return (
              <Text key={pIdx} style={styles.boldText}>
                {part}
              </Text>
            );
          }
          return part;
        });
      };

      if (isBullet) {
        return (
          <View
            key={idx}
            style={[
              styles.bulletRow,
              { paddingLeft: indentLevel > 0 ? indentLevel * 10 : 0 },
            ]}
          >
            <Text style={[styles.bulletPoint, isUser ? styles.messageTextUser : styles.messageTextModel]}>• </Text>
            <Text style={[styles.bulletContent, isUser ? styles.messageTextUser : styles.messageTextModel]}>
              {parseBoldText(cleanLine)}
            </Text>
          </View>
        );
      }

      return (
        <Text
          key={idx}
          style={[
            styles.messageText,
            isUser ? styles.messageTextUser : styles.messageTextModel,
            { marginBottom: cleanLine.trim() === "" ? 4 : 8 },
          ]}
        >
          {parseBoldText(cleanLine)}
        </Text>
      );
    });
  };

  const renderMessageItem = ({ item }: { item: Message }) => {
    const isUser = item.role === "user";
    return (
      <View style={[styles.messageRow, isUser ? styles.messageRowUser : styles.messageRowModel]}>
        <View style={[styles.bubble, isUser ? styles.bubbleUser : styles.bubbleModel]}>
          {renderFormattedText(item.text, isUser)}
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <View style={{ width: 36, height: 36 }} />
        <Text style={styles.headerTitle}>Food Logging AI</Text>
        <TouchableOpacity onPress={clearChat} style={styles.headerBtn}>
          <Ionicons name="trash-outline" size={18} color={COLORS.textPrimary} />
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        style={styles.keyboardContainer}
        keyboardVerticalOffset={Platform.OS === "ios" ? 90 : 100}
      >
        <View style={styles.collapsibleContainer}>
          <TouchableOpacity
            style={styles.collapsibleHeader}
            onPress={() => setShowLogForm(!showLogForm)}
          >
            <Ionicons
              name={showLogForm ? "chevron-up" : "chevron-down"}
              size={16}
              color={COLORS.textPrimary}
              style={{ marginRight: 6 }}
            />
            <Text style={styles.collapsibleTitle}>Quick Log Meal Record</Text>
          </TouchableOpacity>

          {showLogForm && (
            <View style={styles.logFormCard}>
              {logError && <Text style={styles.logErrorText}>{logError}</Text>}
              {logSuccess && <Text style={styles.logSuccessText}>Meal logged successfully!</Text>}
              
              <TextInput
                style={styles.formInput}
                placeholder="Meal Description (e.g. 2 Rotis + Dal)"
                placeholderTextColor={COLORS.textSecondary}
                value={logDesc}
                onChangeText={setLogDesc}
              />
              <View style={styles.formRow}>
                <TextInput
                  style={[styles.formInput, { flex: 1, marginRight: 8 }]}
                  placeholder="Calories (kcal)"
                  placeholderTextColor={COLORS.textSecondary}
                  keyboardType="numeric"
                  value={logCalories}
                  onChangeText={logCalories => setLogCalories(logCalories)}
                />
                <TextInput
                  style={[styles.formInput, { flex: 1 }]}
                  placeholder="Protein (g)"
                  placeholderTextColor={COLORS.textSecondary}
                  keyboardType="numeric"
                  value={logProtein}
                  onChangeText={logProtein => setLogProtein(logProtein)}
                />
              </View>
              <View style={styles.formRow}>
                <TextInput
                  style={[styles.formInput, { flex: 1, marginRight: 8 }]}
                  placeholder="Carbs (g)"
                  placeholderTextColor={COLORS.textSecondary}
                  keyboardType="numeric"
                  value={logCarbs}
                  onChangeText={logCarbs => setLogCarbs(logCarbs)}
                />
                <TextInput
                  style={[styles.formInput, { flex: 1 }]}
                  placeholder="Fat (g)"
                  placeholderTextColor={COLORS.textSecondary}
                  keyboardType="numeric"
                  value={logFat}
                  onChangeText={logFat => setLogFat(logFat)}
                />
              </View>

              <TouchableOpacity
                style={styles.logSubmitBtn}
                onPress={handleLogMealSubmit}
                disabled={loggingMeal}
              >
                {loggingMeal ? (
                  <ActivityIndicator size="small" color="#FFFFFF" />
                ) : (
                  <Text style={styles.logSubmitText}>Log to Database</Text>
                )}
              </TouchableOpacity>
            </View>
          )}
        </View>

        {loadingHistory ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="small" color={COLORS.primary} />
            <Text style={styles.loadingText}>Loading chat history...</Text>
          </View>
        ) : (
          <FlatList
            ref={flatListRef}
            data={messages}
            keyExtractor={(item) => item.id}
            renderItem={renderMessageItem}
            contentContainerStyle={styles.chatList}
            onContentSizeChange={() => flatListRef.current?.scrollToEnd({ animated: true })}
            onLayout={() => flatListRef.current?.scrollToEnd({ animated: true })}
          />
        )}

        {sending && (
          <View style={styles.typingContainer}>
            <ActivityIndicator size="small" color={COLORS.primary} />
            <Text style={styles.typingText}>AI Nutritionist is thinking...</Text>
          </View>
        )}

        <View style={styles.inputBar}>
          <TextInput
            style={styles.textInput}
            placeholder="Type what you ate today..."
            placeholderTextColor={COLORS.textSecondary}
            value={inputText}
            onChangeText={setInputText}
            onSubmitEditing={handleSend}
            returnKeyType="send"
          />
          <TouchableOpacity style={styles.sendBtn} onPress={handleSend} disabled={!inputText.trim() || sending}>
            <Ionicons
              name="send"
              size={18}
              color={inputText.trim() && !sending ? COLORS.primary : COLORS.textSecondary}
            />
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
    paddingTop: Platform.OS === "android" ? StatusBar.currentHeight : 0,
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
  headerTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: COLORS.textPrimary,
  },
  headerBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: COLORS.background,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  keyboardContainer: {
    flex: 1,
  },
  collapsibleContainer: {
    backgroundColor: COLORS.card,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
  },
  collapsibleHeader: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 10,
    paddingHorizontal: SPACING.md,
  },
  collapsibleTitle: {
    fontSize: 13,
    fontWeight: "600",
    color: COLORS.textPrimary,
  },
  logFormCard: {
    paddingHorizontal: SPACING.md,
    paddingBottom: SPACING.md,
  },
  formInput: {
    height: 36,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: SIZES.radiusSm,
    backgroundColor: COLORS.background,
    color: COLORS.textPrimary,
    paddingHorizontal: SPACING.sm,
    fontSize: 13,
    marginBottom: 8,
  },
  formRow: {
    flexDirection: "row",
    marginBottom: 0,
  },
  logSubmitBtn: {
    height: 36,
    backgroundColor: COLORS.primary,
    borderRadius: SIZES.radiusSm,
    justifyContent: "center",
    alignItems: "center",
    marginTop: 4,
  },
  logSubmitText: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "600",
  },
  logErrorText: {
    color: COLORS.error,
    fontSize: 12,
    marginBottom: 6,
    fontWeight: "500",
  },
  logSuccessText: {
    color: COLORS.success,
    fontSize: 12,
    marginBottom: 6,
    fontWeight: "500",
  },
  chatList: {
    padding: SPACING.md,
    paddingBottom: SPACING.xl,
  },
  messageRow: {
    flexDirection: "row",
    marginBottom: SPACING.md,
    width: "100%",
  },
  messageRowUser: {
    justifyContent: "flex-end",
  },
  messageRowModel: {
    justifyContent: "flex-start",
  },
  bubble: {
    borderRadius: SIZES.radiusMd,
    paddingVertical: 10,
    paddingHorizontal: 14,
    maxWidth: "85%",
    borderWidth: 1,
  },
  bubbleUser: {
    backgroundColor: COLORS.primary,
    borderColor: COLORS.primary,
    borderBottomRightRadius: 2,
  },
  bubbleModel: {
    backgroundColor: COLORS.card,
    borderColor: COLORS.border,
    borderBottomLeftRadius: 2,
  },
  messageText: {
    fontSize: 13,
    lineHeight: 18,
  },
  messageTextUser: {
    color: "#FFFFFF",
    fontWeight: "500",
  },
  messageTextModel: {
    color: COLORS.textPrimary,
  },
  boldText: {
    fontWeight: "bold",
  },
  bulletRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    marginBottom: 4,
  },
  bulletPoint: {
    fontSize: 13,
    lineHeight: 18,
  },
  bulletContent: {
    flex: 1,
    fontSize: 13,
    lineHeight: 18,
  },
  typingContainer: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    backgroundColor: COLORS.background,
  },
  typingText: {
    fontSize: 12,
    color: COLORS.textSecondary,
    marginLeft: 6,
    fontWeight: "500",
  },
  inputBar: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    borderTopWidth: 1,
    borderTopColor: COLORS.border,
    backgroundColor: COLORS.background,
  },
  textInput: {
    flex: 1,
    height: 38,
    borderRadius: 19,
    borderWidth: 1,
    borderColor: COLORS.border,
    paddingHorizontal: SPACING.md,
    backgroundColor: COLORS.accent,
    color: COLORS.textPrimary,
    fontSize: 14,
  },
  sendBtn: {
    marginLeft: SPACING.sm,
    width: 36,
    height: 36,
    justifyContent: "center",
    alignItems: "center",
  },
  loadingContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: SPACING.xl,
  },
  loadingText: {
    fontSize: 13,
    color: COLORS.textSecondary,
    marginTop: 8,
    fontWeight: "500",
  },
});
