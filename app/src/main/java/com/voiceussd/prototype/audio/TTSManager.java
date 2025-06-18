package com.voiceussd.prototype.audio;

import android.content.Context; // Retrieves resources needed by the app
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TTSManager implements TextToSpeech.OnInitListener{
    private static final String TAG = "TTSManager";
    private TextToSpeech tts;
    private boolean isTTSReady = false;
    private Context context;

    public TTSManager(Context context){
        this.context = context;
        initializeTTS();
    }

    public void initializeTTS(){
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status){
        if(status == TextToSpeech.SUCCESS){
            // Set language (fallback to English for now
            int result = tts.setLanguage(Locale.ENGLISH);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                Log.e(TAG, "Language not supported");
            } else {
                isTTSReady = true;

                // Configure for USSD speed requirements
                tts.setSpeechRate(1.0f);
                tts.setPitch(1.0f);

                // Set up completion listener
                setupUtteranceListener();

                Log.d(TAG, "TTS initialized successfully");
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }

    // Add STT callback interface
    private STTTriggerCallback sttCallback;

    public interface STTTriggerCallback {
        void onTTSFinished();
    }

    public void setSTTCallback(STTTriggerCallback callback) {
        this.sttCallback = callback;
    }

    // Update the existing onDone method
    private void setupUtteranceListener(){
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override
            public void onStart(String utteranceId){
                Log.d(TAG, "=== TTS STARTED READING ===");
            }

            @Override
            public void onDone(String utteranceId){
                if (utteranceId.equals("ussd_menu")) {
                    Log.d(TAG, "=== TTS FINISHED - READY TO LISTEN ===");

                    // Trigger STT in USSDDetectorService
                    if (sttCallback != null) {
                        sttCallback.onTTSFinished();
                    }
                }
            }

            @Override
            public void onError(String utteranceId){
                Log.e(TAG, "TTS error occurred");
            }
        });
    }

    public void speakMenu(String ussdText){
        if (!isTTSReady){
            Log.w(TAG, "TTS not ready yet");
            return;
        }

        List<String> menuOptions = parseMenuOptions(ussdText);

        if (menuOptions.isEmpty()) {
            Log.w(TAG, "No menu options found to speak");
            return;
        }

        // Create speech text from parsed options
        String speechText = createSpeechText(menuOptions);
        Log.d(TAG, "Speaking menu: " + speechText);

        // Speak with utterance ID for tracking speech completion
        // NEW - Current API
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ussd_menu");
        tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, "ussd_menu");
    }

    private List<String> parseMenuOptions(String ussdText) {
        List<String> options = new ArrayList<>();

        // Pattern to match numbered menu items
        Pattern pattern = Pattern.compile("(\\d+)\\)\\s*(.+?)(?=\\n\\d+\\)|\\nn\\s|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(ussdText);

        while (matcher.find()){
            String number = matcher.group(1);
            String text = Objects.requireNonNull(matcher.group(2)).trim();

            // Clean up the text (remove whitespaces/new lines
            text = text.replaceAll("\\s+", " ").trim();

            String option = number + ": " + text;
            options.add(option);

            Log.d(TAG, "Parsed: " + option);
        }

        return options;
    }

    private String createSpeechText(List<String> menuOptions){
        StringBuilder speechBuilder = new StringBuilder();

        // Add intro
//        speechBuilder.append("Vuga nimero ya serivise wifuza. ");
        speechBuilder.append("Say the number representing the service you want.");

        // Add all options
        for(String option: menuOptions){
            speechBuilder.append(option).append(". ");
        }

        // Add instruction
//        speechBuilder.append("Vuga nomero ya serivise ushaka");

        return speechBuilder.toString();
    }

    public boolean isReady(){
        return isTTSReady;
    }

    public void shutdown(){
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TTS shutdown");
        }
    }
}













