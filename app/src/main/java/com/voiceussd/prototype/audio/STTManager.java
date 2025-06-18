package com.voiceussd.prototype.audio;

import android.content.Context;
import android.content.Intent; // Launches activities
import android.os.Bundle;
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
        if(SpeechRecognizer.isRecognitionAvailable(context)){
            Log.d(TAG, "STT: ✅ Speech recognition IS available");
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(this);

            Log.d(TAG, "STT: ✅ SpeechRecognizer created: " + (speechRecognizer != null));
            Log.d(TAG, "STT: ✅ Recognition listener set");


            // Recognition intent configurations
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

            // Specifies type of speech to expect (FREE_FORM = natural speech, WEB_SEARCH = short commands)
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);

            // Set language to recognize
//            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toString());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");

            // Don't get partial results while speaking, wait for user to end speech
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

            // Limit possible speech interpretations
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);

            // Speech recognition Lower confidence scores
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true);

            // User done speaking
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);

            // Allow pause mid-speech/ prepare stt service to stop
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);

            Log.d(TAG, "STT Initialized Successfully");

            if (callback != null){
                Log.d(TAG, "STT: ✅ Calling onSTTReady callback");
                callback.onSTTReady();
            } else {
                Log.w(TAG, "STT Warning: ⚠️ No callback set for onSTTReady");
            }

        }else{
            Log.e(TAG, "STT ERROR: ❌Speech recognition not available on this device");
            if (callback != null){
                callback.onSTTError("STT Error: Speech recongition not available");
            }
        }
    }

    public void startListening(){
        if(!isListening && speechRecognizer != null){
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "=== STT STARTED LISTENING ===");
        }
    }

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
        Log.d(TAG, "=== READY FOR SPEECH ===");
    }

    @Override
    public void onBeginningOfSpeech(){
        Log.d(TAG, "Beginning of detected speech");
    }

    @Override
    public void onRmsChanged(float rmsdb){
        // Audio level changes - can be used for visual feedback
        Log.d(TAG, "=== AUDIO LEVEL: " + rmsdb + " dB");
    }

    @Override
    public void onBufferReceived(byte[] buffer){
        // Raw audio bugger - not needed for our use case
    }

    @Override
    public void onEndOfSpeech(){
        Log.d(TAG, "End of speech detected");
        isListening = false;
    }

    @Override
    public void onError(int error){
        isListening = false;
        String errorMessage = getErrorText(error);
        Log.e(TAG, "STT Error: " + errorMessage);

        if (callback != null){
            callback.onSTTError(errorMessage);
        }
    }

    @Override
    public void onResults(Bundle results){
        isListening = false;

        Log.d(TAG, "=== YAAAAYYYY onResults() CALLED ==="+ results);
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if(matches != null && !matches.isEmpty()){

            Log.d(TAG, "=== SPEECH RESULTS RECEIVED ===");

            for(int i = 0; i < matches.size(); i++) {
                Log.d(TAG, "Result " + i + ": " + matches.get(i));
            }

            String recognizedText = matches.get(0);
            Log.d(TAG, "Recognized: " + recognizedText);

            int menuNumber = extractMenuNumber(recognizedText);

            if(menuNumber != - 1){
                Log.d(TAG, "=== MENU NUMBER EXTRACTED: " + menuNumber + " ===");
                if(callback != null){
                    callback.onNumberRecognized(menuNumber);
                }
            }else{
                Log.w(TAG, "Warning: Could not extract valid menu number");
                // Continue listening
                startListening();
            }
        }else {
            Log.e(TAG, "=== NO SPEECH MATCHES FOUND ===");
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults){
        // Not necessary for now, the system waits for user to finish speech
        /*
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            Log.d(TAG, "Partial: " + matches.get(0));
        */
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
