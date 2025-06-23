package com.voiceussd.prototype.audio;

import android.content.Context;
import android.content.Intent; // Launches activities
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

public class STTManager implements RecognitionListener{
    private static final String TAG = "STTManager";
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private Context context;
    private boolean isListening = false;
    private STTCallback callback;

    // Interface for communicating back to USSDDetectorService
    public interface STTCallback{
        void onNumberRecognized(int number);
        void onSTTError(String error);
        void onSTTReady();
    }

    public STTManager(Context context, STTCallback callback){
        this.context = context;
        this.callback = callback;
        initializeSTT();
    }

    public void setCallback(STTCallback callback){
        this.callback = callback;
    }

    private void initializeSTT(){
        Log.d(TAG, "=== INITIALIZING STT ===");

        if(SpeechRecognizer.isRecognitionAvailable(context)){
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

            // Try different configurations
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");

            // IMPORTANT: Enable partial results for better debugging
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            // More results and confidence
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true);

            // Shorter timeouts for single words
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);

            // Add calling package
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

            Log.d(TAG, "✅ STT Intent configured");
            Log.d(TAG, "Language Model: " + RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            Log.d(TAG, "Language: en-US");
            Log.d(TAG, "Max Results: 3");
            Log.d(TAG, "Partial Results: true");

            if (callback != null){
                callback.onSTTReady();
            }

        } else {
            Log.e(TAG, "❌ Speech recognition NOT available on this device");
            if (callback != null){
                callback.onSTTError("Speech recognition not available");
            }
        }
    }
    // Add this to STTManager.java
    private boolean testMode = false; // Set to false for real STT

    public void startListening(){
        if (testMode) {
            // Your existing simulation code
            Log.d(TAG, "=== TEST MODE: SIMULATING STT ===");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Bundle fakeResults = new Bundle();
                ArrayList<String> fakeMatches = new ArrayList<>();
                fakeMatches.add("one");
                fakeResults.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, fakeMatches);
                onResults(fakeResults);
            }, 2000);
            return;
        }

        Log.d(TAG, "=== STARTING REAL STT ===");

        if (speechRecognizer == null) {
            Log.e(TAG, "❌ SpeechRecognizer is null!");
            return;
        }

        if (isListening) {
            Log.w(TAG, "⚠️ Already listening, stopping first...");
            speechRecognizer.stopListening();
            isListening = false;

            // Wait a moment before restarting
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startListeningInternal();
            }, 500);
            return;
        }

        startListeningInternal();
    }

//    private void startListeningInternal() {
//        try {
//            isListening = true;
//            Log.d(TAG, "🎤 Calling speechRecognizer.startListening()...");
//            speechRecognizer.startListening(recognizerIntent);
//            Log.d(TAG, "✅ startListening() call completed");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Exception during startListening(): " + e.getMessage());
//            isListening = false;
//            if (callback != null) {
//                callback.onSTTError("Failed to start listening: " + e.getMessage());
//            }
//        }
//    }

    public void stopListening(){
        if(isListening && speechRecognizer != null){
            isListening = false;
            speechRecognizer.stopListening();
            Log.d(TAG, "=== STT STOPPED LISTENING ===");
        }
    }

    public boolean isReady(){
        return speechRecognizer != null && !isListening;
    }

    public void shutdown(){
        if(speechRecognizer != null){
            speechRecognizer.destroy();
            speechRecognizer = null;
            Log.d(TAG, "STT Shutdown");
        }else{
            Log.e(TAG, "WARNING: STT Already shutdown");
        }
    }

    // Extract single menu number from speech
    private int extractMenuNumber(String speech){
        String lowerSpeech = speech.toLowerCase();
        Log.d(TAG, "===> Parsing speech: " + speech);

        // Direct digit recognition
        if(lowerSpeech.contains("zero")) return 0;
        if(lowerSpeech.contains("one") || lowerSpeech.contains("1")) return 1;
        if(lowerSpeech.contains("two")) return 2;
        if(lowerSpeech.contains("three")) return 3;
        if(lowerSpeech.contains("four")) return 4;
        if(lowerSpeech.contains("five")) return 5;
        if(lowerSpeech.contains("six")) return 6;
        if(lowerSpeech.contains("seven")) return 7;
        if(lowerSpeech.contains("eight")) return 8;
        if(lowerSpeech.contains("nine")) return 9;

        Log.w(TAG, "No vaid menu number found in: " + speech);
        return -1; // invalid
    }

    // RecognitionListener implementation
    @Override
    public void onReadyForSpeech(Bundle params){
        Log.d(TAG, "🟢 === READY FOR SPEECH ===");
        if (params != null) {
            for (String key : params.keySet()) {
                Log.d(TAG, "Ready params[" + key + "] = " + params.get(key));
            }
        }
    }

    @Override
    public void onBeginningOfSpeech(){
        Log.d(TAG, "🔵 === BEGINNING OF SPEECH DETECTED ===");
    }

    @Override
    public void onRmsChanged(float rmsdb){
        // Only log significant changes to avoid spam
        float lastRms = -999;
        if (Math.abs(rmsdb - lastRms) > 1.0f) {
            Log.d(TAG, "🔊 Audio Level: " + rmsdb + " dB");
            lastRms = rmsdb;
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer){
        Log.d(TAG, "📊 Audio buffer received: " + (buffer != null ? buffer.length + " bytes" : "null"));
    }

    @Override
    public void onEndOfSpeech(){
        Log.d(TAG, "🔴 === END OF SPEECH DETECTED ===");
        isListening = false;
    }

    @Override
    public void onError(int error){
        isListening = false;
        String errorMessage = getErrorText(error);
        Log.e(TAG, "❌ === STT ERROR: " + errorMessage + " (Code: " + error + ") ===");

        // Log additional context
        Log.e(TAG, "Error occurred after " + (System.currentTimeMillis() - startTime) + "ms");

        if (callback != null){
            callback.onSTTError(errorMessage);
        }

        // Don't auto-retry immediately to avoid infinite loops
        Log.d(TAG, "Not auto-retrying. User needs to trigger manually.");
    }

    @Override
    public void onResults(Bundle results){
        isListening = false;
        Log.d(TAG, "🎯 === onResults() CALLED ===");

        if (results == null) {
            Log.e(TAG, "❌ Results bundle is NULL");
            return;
        }

        // Log EVERYTHING in the bundle
        Log.d(TAG, "📋 Results bundle contents:");
        for (String key : results.keySet()) {
            Object value = results.get(key);
            if (value instanceof ArrayList) {
                ArrayList<?> list = (ArrayList<?>) value;
                Log.d(TAG, "  " + key + " = ArrayList[" + list.size() + "]: " + list.toString());
            } else {
                Log.d(TAG, "  " + key + " = " + value);
            }
        }

        // Try to get results with multiple approaches
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (matches == null) {
            Log.e(TAG, "❌ RESULTS_RECOGNITION is null");

            // Try alternative keys
            Object altResults = results.get("android.speech.extra.RESULTS");
            Log.d(TAG, "Alternative results key: " + altResults);

            return;
        }

        if (matches.isEmpty()) {
            Log.e(TAG, "❌ RESULTS_RECOGNITION is empty");
            return;
        }

        Log.d(TAG, "✅ === SPEECH RESULTS RECEIVED ===");
        Log.d(TAG, "Number of matches: " + matches.size());

        for(int i = 0; i < matches.size(); i++) {
            String match = matches.get(i);
            Log.d(TAG, "  Result[" + i + "]: '" + match + "' (length=" + match.length() + ")");
        }

        // Also check confidence scores if available
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (confidences != null) {
            Log.d(TAG, "Confidence scores:");
            for (int i = 0; i < confidences.length && i < matches.size(); i++) {
                Log.d(TAG, "  '" + matches.get(i) + "' confidence: " + confidences[i]);
            }
        }

        // Process the results
        String recognizedText = matches.get(0);
        Log.d(TAG, "Processing: '" + recognizedText + "'");

        int menuNumber = extractMenuNumber(recognizedText);

        if(menuNumber != -1){
            Log.d(TAG, "✅ === EXTRACTED NUMBER: " + menuNumber + " ===");
            if(callback != null){
                callback.onNumberRecognized(menuNumber);
            }
        } else {
            Log.w(TAG, "⚠️ No valid number found in: '" + recognizedText + "'");
            // Don't auto-restart to avoid loops
        }
    }

    // process partial results immediately
    @Override
    public void onPartialResults(Bundle partialResults){
        if (partialResults != null) {
            ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (partialMatches != null && !partialMatches.isEmpty()) {
                String partialText = partialMatches.get(0);
                Log.d(TAG, "🔄 Partial: '" + partialText + "'");

                // Check if we have a clear number
                int number = extractMenuNumber(partialText);
                if (number != -1) {
                    Log.d(TAG, "✅ === FOUND NUMBER IN PARTIAL: " + number + " ===");

                    // Stop listening immediately
                    if (speechRecognizer != null && isListening) {
                        speechRecognizer.stopListening();
                        isListening = false;
                    }

                    // Process the result
                    if (callback != null) {
                        callback.onNumberRecognized(number);
                    }
                }
            }
        }
    }

    // Add a start time tracker
    private long startTime;

    // Modify your startListeningInternal to track timing
    private void startListeningInternal() {
        try {
            isListening = true;
            startTime = System.currentTimeMillis();
            Log.d(TAG, "🎤 Starting speech recognition at " + startTime);
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "✅ startListening() call completed");
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception: " + e.getMessage());
            isListening = false;
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params){
        // Not used in our implementation
    }

    private String getErrorText(int errorCode){
        switch(errorCode){
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
                return "--- UNKNOWN ERROR ---";
        }
    }
}
