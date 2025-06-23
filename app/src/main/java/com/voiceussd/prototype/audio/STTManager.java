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

    // NEW: Add input mode
    private InputMode currentMode = InputMode.MENU;
    private StringBuilder longInputBuffer = new StringBuilder();
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    // NEW: Input modes
    public enum InputMode {
        MENU,      // Single digit (existing working functionality)
        LONG_INPUT // Multi-digit real-time input
    }

    // Interface for communicating back to USSDDetectorService
    public interface STTCallback {
        void onNumberRecognized(int number);        // For menu (existing)
        void onDigitRecognized(int digit);          // NEW: For real-time digits
        void onLongInputCompleted(String fullInput); // NEW: When 3s timeout reached
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

    // NEW: Method to switch input modes
    public void setInputMode(InputMode mode) {
        this.currentMode = mode;
        if (mode == InputMode.LONG_INPUT) {
            longInputBuffer.setLength(0); // Clear buffer
            setupLongInputConfiguration();
        } else {
            setupMenuConfiguration();
        }
        Log.d(TAG, "Switched to input mode: " + mode);
    }

    private void setupMenuConfiguration() {
        // Keep your existing working configuration
        if (recognizerIntent == null) return;

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        Log.d(TAG, "Configured for MENU mode (1.5s timeout)");
    }

    private void setupLongInputConfiguration() {
        // Shorter timeouts for real-time digit capture
        if (recognizerIntent == null) return;

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        Log.d(TAG, "Configured for LONG_INPUT mode (0.8s timeout for digits)");
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

            // Create and configure intent (keep your working configuration)
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
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
            }, 500);
            return;
        }

        startListeningInternal();
    }

    private void startListeningInternal() {
        try {
            isListening = true;
            Log.d(TAG, "🎤 Starting " + currentMode + " recognition...");

            // For long input, start the 3-second completion timeout
            if (currentMode == InputMode.LONG_INPUT) {
                startLongInputTimeout();
            }

            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "✅ startListening() call completed");
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception: " + e.getMessage());
            isListening = false;
        }
    }

    private void startLongInputTimeout() {
        // Cancel any existing timeout
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        // Set 3-second timeout for long input completion
        timeoutRunnable = () -> {
            if (currentMode == InputMode.LONG_INPUT && longInputBuffer.length() > 0) {
                Log.d(TAG, "⏰ Long input timeout reached. Completing input: " + longInputBuffer.toString());
                if (callback != null) {
                    callback.onLongInputCompleted(longInputBuffer.toString());
                }
                stopListening();
            }
        };

        timeoutHandler.postDelayed(timeoutRunnable, 3000); // 3 seconds
    }

    private void resetLongInputTimeout() {
        if (currentMode == InputMode.LONG_INPUT && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            startLongInputTimeout(); // Restart the 3-second timer
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

    // Extract single digit from speech (keep your working logic)
    private int extractMenuNumber(String speech) {
        String lowerSpeech = speech.toLowerCase();
        Log.d(TAG, "===> Parsing speech: " + speech);

        // Your existing working logic
        if (lowerSpeech.contains("zero")) return 0;
        if (lowerSpeech.contains("one") || lowerSpeech.contains("1")) return 1;
        if (lowerSpeech.contains("two")) return 2;
        if (lowerSpeech.contains("three")) return 3;
        if (lowerSpeech.contains("four")) return 4;
        if (lowerSpeech.contains("five")) return 5;
        if (lowerSpeech.contains("six")) return 6;
        if (lowerSpeech.contains("seven")) return 7;
        if (lowerSpeech.contains("eight")) return 8;
        if (lowerSpeech.contains("nine")) return 9;

        Log.w(TAG, "No valid menu number found in: " + speech);
        return -1;
    }

    // RecognitionListener implementation (keep your working methods)
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

        // For long input, automatically restart listening unless timeout occurred
        if (currentMode == InputMode.LONG_INPUT) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isListening && speechRecognizer != null) {
                    startListening(); // Continue listening for more digits
                }
            }, 200); // Short delay before restarting
        }
    }

    @Override
    public void onError(int error) {
        isListening = false;
        String errorMessage = getErrorText(error);
        Log.e(TAG, "❌ === STT ERROR: " + errorMessage + " ===");

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

        String recognizedText = matches.get(0);
        Log.d(TAG, "Processing: '" + recognizedText + "' for mode: " + currentMode);

        int digit = extractMenuNumber(recognizedText);

        if (digit != -1) {
            if (currentMode == InputMode.MENU) {
                // Existing working functionality for menu
                Log.d(TAG, "✅ === EXTRACTED MENU NUMBER: " + digit + " ===");
                if (callback != null) {
                    callback.onNumberRecognized(digit);
                }
            } else if (currentMode == InputMode.LONG_INPUT) {
                // NEW: Real-time digit capture
                longInputBuffer.append(digit);
                Log.d(TAG, "✅ === CAPTURED DIGIT: " + digit + " (Buffer: " + longInputBuffer.toString() + ") ===");

                if (callback != null) {
                    callback.onDigitRecognized(digit);
                }

                // Reset the 3-second timeout
                resetLongInputTimeout();
            }
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (partialResults != null) {
            ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (partialMatches != null && !partialMatches.isEmpty()) {
                String partialText = partialMatches.get(0);
                Log.d(TAG, "🔄 Partial (" + currentMode + "): '" + partialText + "'");

                // Check if we have a clear number
                int digit = extractMenuNumber(partialText);
                if (digit != -1) {
                    Log.d(TAG, "✅ === FOUND " + currentMode + " NUMBER IN PARTIAL: " + digit + " ===");

                    // Stop listening immediately for quicker response
                    if (speechRecognizer != null && isListening) {
                        speechRecognizer.stopListening();
                        isListening = false;
                    }

                    // Process the result
                    if (currentMode == InputMode.MENU) {
                        if (callback != null) {
                            callback.onNumberRecognized(digit);
                        }
                    } else if (currentMode == InputMode.LONG_INPUT) {
                        longInputBuffer.append(digit);
                        if (callback != null) {
                            callback.onDigitRecognized(digit);
                        }
                        resetLongInputTimeout();
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
        // Keep your existing error handling
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