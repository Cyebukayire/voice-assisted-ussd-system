package com.voiceussd.prototype.audio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

public class STTManager implements RecognitionListener {
    private static final String TAG = "STTManager";
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private Context context;
    private boolean isListening = false;
    private STTCallback callback;

    // NEW: Enhanced input modes with digit-by-digit approach
    private InputMode currentMode = InputMode.MENU;
    private StringBuilder longInputBuffer = new StringBuilder();
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    public enum InputMode {
        MENU,           // Single digit for menu selection (existing working functionality)
        DIGIT_BY_DIGIT  // NEW: Single digit sessions with confirmation loop
    }

    // Enhanced interface for digit-by-digit flow
    public interface STTCallback {
        void onNumberRecognized(int number);           // For menu (existing)
        void onDigitRecognized(int digit);             // NEW: Single digit captured
        void onDoneCommandRecognized();                // NEW: User said "done"
        void onLongInputCompleted(String fullInput);   // NEW: Timeout or completion
        void onSTTError(String error);
        void onSTTReady();
    }

    public STTManager(Context context, STTCallback callback) {
        this.context = context;
        this.callback = callback;
        initializeSTT();
    }

    public void setCallback(STTCallback callback) {
        this.callback = callback;
    }

    public void setInputMode(InputMode mode) {
        this.currentMode = mode;
        if (mode == InputMode.DIGIT_BY_DIGIT) {
            longInputBuffer.setLength(0); // Clear buffer
            setupDigitByDigitConfiguration();
        } else {
            setupMenuConfiguration();
        }
        Log.d(TAG, "Switched to input mode: " + mode);
    }

    private void setupMenuConfiguration() {
        if (recognizerIntent == null) return;

        // Keep your existing working menu configuration
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        Log.d(TAG, "Configured for MENU mode (1.5s timeout)");
    }

    private void setupDigitByDigitConfiguration() {
        if (recognizerIntent == null) return;

        // NEW: Use same timeouts as menu (which work great!) for individual digit sessions
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        Log.d(TAG, "Configured for DIGIT_BY_DIGIT mode (1.5s timeout per digit)");
    }

    private void initializeSTT() {
        Log.d(TAG, "=== INITIALIZING STT ===");

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.d(TAG, "✅ Speech recognition IS available");

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            if (speechRecognizer == null) {
                Log.e(TAG, "❌ Failed to create SpeechRecognizer instance");
                return;
            }

            speechRecognizer.setRecognitionListener(this);
            Log.d(TAG, "✅ Recognition listener set");

            // Create and configure intent
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

            // Apply initial configuration
            setupMenuConfiguration();

            if (callback != null) {
                callback.onSTTReady();
            }

        } else {
            Log.e(TAG, "❌ Speech recognition NOT available on this device");
            if (callback != null) {
                callback.onSTTError("Speech recognition not available");
            }
        }
    }

    public void startListening() {
        Log.d(TAG, "=== STARTING STT FOR MODE: " + currentMode + " ===");

        if (speechRecognizer == null) {
            Log.e(TAG, "❌ SpeechRecognizer is null!");
            return;
        }

        if (isListening) {
            Log.w(TAG, "⚠️ Already listening, stopping first...");
            speechRecognizer.stopListening();
            isListening = false;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startListeningInternal();
            }, 1000);
            return;
        }

        startListeningInternal();
    }

    private void startListeningInternal() {
        try {
            isListening = true;
            Log.d(TAG, "🎤 Starting " + currentMode + " recognition...");

            // NEW: For digit-by-digit, start a completion timeout (longer than individual digit timeout)
            if (currentMode == InputMode.DIGIT_BY_DIGIT) {
                startCompletionTimeout();
            }

            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "✅ startListening() call completed");
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception: " + e.getMessage());
            isListening = false;
        }
    }

    private void startCompletionTimeout() {
        // Cancel any existing timeout
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // NEW: 8-second timeout for overall completion (user has time to say "done")
        timeoutRunnable = () -> {
            if (currentMode == InputMode.DIGIT_BY_DIGIT && longInputBuffer.length() > 0) {
                Log.d(TAG, "⏰ Overall completion timeout reached (8s). Completing input: " + longInputBuffer.toString());
                if (callback != null) {
                    callback.onLongInputCompleted(longInputBuffer.toString());
                }
                stopListening();
            }
        };

        timeoutHandler.postDelayed(timeoutRunnable, 8000); // 8 seconds total
    }

    private void resetCompletionTimeout() {
        if (currentMode == InputMode.DIGIT_BY_DIGIT && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            startCompletionTimeout(); // Restart the 8-second timer
        }
    }

    public void stopListening() {
        if (isListening && speechRecognizer != null) {
            isListening = false;
            speechRecognizer.stopListening();

            // Cancel timeout if stopping manually
            if (timeoutRunnable != null) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
            }

            Log.d(TAG, "=== STT STOPPED LISTENING ===");
        }
    }

    public boolean isReady() {
        return speechRecognizer != null && !isListening;
    }

    public void shutdown() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;

            // Clean up timeout handler
            if (timeoutRunnable != null) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
            }

            Log.d(TAG, "STT Shutdown");
        }
    }

    // ENHANCED: Better digit and command extraction
    private int extractDigit(String speech) {
        String lowerSpeech = speech.toLowerCase().trim();
        Log.d(TAG, "===> Parsing speech for digit: '" + speech + "'");

        // Handle various ways people say digits
        if (lowerSpeech.contains("zero") || lowerSpeech.equals("0")) return 0;
        if (lowerSpeech.contains("one") || lowerSpeech.equals("1") || lowerSpeech.contains("won")) return 1;
        if (lowerSpeech.contains("two") || lowerSpeech.equals("2") || lowerSpeech.contains("too")) return 2;
        if (lowerSpeech.contains("three") || lowerSpeech.equals("3") || lowerSpeech.contains("tree")) return 3;
        if (lowerSpeech.contains("four") || lowerSpeech.equals("4") || lowerSpeech.contains("for")) return 4;
        if (lowerSpeech.contains("five") || lowerSpeech.equals("5")) return 5;
        if (lowerSpeech.contains("six") || lowerSpeech.equals("6") || lowerSpeech.contains("sicks")) return 6;
        if (lowerSpeech.contains("seven") || lowerSpeech.equals("7")) return 7;
        if (lowerSpeech.contains("eight") || lowerSpeech.equals("8") || lowerSpeech.contains("ate")) return 8;
        if (lowerSpeech.contains("nine") || lowerSpeech.equals("9") || lowerSpeech.contains("nein")) return 9;

        // Try to extract pure numbers
        String digitsOnly = speech.replaceAll("[^0-9]", "");
        if (digitsOnly.length() == 1) {
            try {
                return Integer.parseInt(digitsOnly);
            } catch (NumberFormatException e) {
                // Continue to no match
            }
        }

        Log.w(TAG, "No valid digit found in: '" + speech + "'");
        return -1;
    }

    // NEW: Check for completion commands
    private boolean isDoneCommand(String speech) {
        String lowerSpeech = speech.toLowerCase().trim();
        return lowerSpeech.contains("done") ||
                lowerSpeech.contains("finished") ||
                lowerSpeech.contains("complete") ||
                lowerSpeech.contains("send") ||
                lowerSpeech.equals("end");
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "🟢 === READY FOR " + currentMode + " SPEECH ===");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "🔵 === BEGINNING OF " + currentMode + " SPEECH ===");
    }

    @Override
    public void onRmsChanged(float rmsdb) {
        // Keep your existing implementation
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.d(TAG, "📊 Audio buffer received: " + (buffer != null ? buffer.length + " bytes" : "null"));
    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "🔴 === END OF " + currentMode + " SPEECH ===");
        isListening = false;

        // NEW: No automatic restart - wait for explicit command from USSDDetectorService
        // This is the key change that implements our "menu-like loop" strategy
        Log.d(TAG, "Waiting for explicit restart command...");
    }

    @Override
    public void onError(int error) {
        isListening = false;
        String errorMessage = getErrorText(error);
        Log.e(TAG, "❌ === STT ERROR: " + errorMessage + " (Code: " + error + ") ===");

        // NEW: For digit-by-digit mode, some errors can be handled gracefully
        if (currentMode == InputMode.DIGIT_BY_DIGIT) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                // Don't report as error, just let the system continue
                Log.d(TAG, "No match in digit-by-digit mode, ready for next attempt");
                return;
            }
        }

        if (callback != null) {
            callback.onSTTError(errorMessage);
        }
    }

    @Override
    public void onResults(Bundle results) {
        isListening = false;
        Log.d(TAG, "🎯 === onResults() CALLED FOR " + currentMode + " ===");

        if (results == null) {
            Log.e(TAG, "❌ Results bundle is NULL");
            return;
        }

        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            Log.e(TAG, "❌ No recognition results");
            return;
        }

        // Try all matches to find the best result
        for (String match : matches) {
            Log.d(TAG, "Trying match: '" + match + "'");

            if (currentMode == InputMode.MENU) {
                // KEEP: Existing working menu logic
                int digit = extractDigit(match);
                if (digit != -1) {
                    Log.d(TAG, "✅ === EXTRACTED MENU NUMBER: " + digit + " ===");
                    if (callback != null) {
                        callback.onNumberRecognized(digit);
                    }
                    return;
                }
            } else if (currentMode == InputMode.DIGIT_BY_DIGIT) {
                // NEW: Check for "done" command first
                if (isDoneCommand(match)) {
                    Log.d(TAG, "✅ === USER SAID DONE ===");
                    if (callback != null) {
                        callback.onDoneCommandRecognized();
                    }
                    return;
                }

                // Then check for digit
                int digit = extractDigit(match);
                if (digit != -1) {
                    longInputBuffer.append(digit);
                    Log.d(TAG, "✅ === CAPTURED DIGIT: " + digit + " (Buffer: " + longInputBuffer.toString() + ") ===");

                    if (callback != null) {
                        callback.onDigitRecognized(digit);
                    }

                    // Reset the completion timeout
                    resetCompletionTimeout();
                    return;
                }
            }
        }

        Log.w(TAG, "No valid result found in any of the " + matches.size() + " matches");
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (partialResults != null) {
            ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (partialMatches != null && !partialMatches.isEmpty()) {
                String partialText = partialMatches.get(0);
                Log.d(TAG, "🔄 Partial (" + currentMode + "): '" + partialText + "'");

                if (currentMode == InputMode.MENU) {
                    // KEEP: Existing working partial logic for menu
                    int digit = extractDigit(partialText);
                    if (digit != -1) {
                        Log.d(TAG, "✅ === FOUND MENU NUMBER IN PARTIAL: " + digit + " ===");

                        if (speechRecognizer != null && isListening) {
                            speechRecognizer.stopListening();
                            isListening = false;
                        }

                        if (callback != null) {
                            callback.onNumberRecognized(digit);
                        }
                    }
                } else if (currentMode == InputMode.DIGIT_BY_DIGIT) {
                    // NEW: Handle partial results for digit-by-digit
                    if (isDoneCommand(partialText)) {
                        Log.d(TAG, "✅ === FOUND DONE COMMAND IN PARTIAL ===");

                        if (speechRecognizer != null && isListening) {
                            speechRecognizer.stopListening();
                            isListening = false;
                        }

                        if (callback != null) {
                            callback.onDoneCommandRecognized();
                        }
                    } else {
                        int digit = extractDigit(partialText);
                        if (digit != -1) {
                            Log.d(TAG, "✅ === FOUND DIGIT IN PARTIAL: " + digit + " ===");

                            if (speechRecognizer != null && isListening) {
                                speechRecognizer.stopListening();
                                isListening = false;
                            }

                            longInputBuffer.append(digit);
                            if (callback != null) {
                                callback.onDigitRecognized(digit);
                            }
                            resetCompletionTimeout();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        // Not used
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "AUDIO recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No match found";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognition service busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No Speech input";
            default:
                return "Unknown error";
        }
    }
}