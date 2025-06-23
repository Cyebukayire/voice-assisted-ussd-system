package com.voiceussd.prototype.audio;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TTSManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "TTSManager";
    private TextToSpeech tts;
    private boolean isTTSReady = false;
    private Context context;

    public TTSManager(Context context) {
        this.context = context;
        initializeTTS();
    }

    public void initializeTTS() {
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.ENGLISH);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
            } else {
                isTTSReady = true;
                tts.setSpeechRate(1.0f);
                tts.setPitch(1.0f);
                setupUtteranceListener();
                Log.d(TAG, "TTS initialized successfully");
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }

    // KEEP: Your existing STT callback interface
    private STTTriggerCallback sttCallback;

    public interface STTTriggerCallback {
        void onTTSFinished();
    }

    public void setSTTCallback(STTTriggerCallback callback) {
        this.sttCallback = callback;
    }

    // KEEP: Your existing utterance listener
    private void setupUtteranceListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "=== TTS STARTED READING ===");
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "=== TTS FINISHED - utteranceId: " + utteranceId + " ===");

                // Only trigger STT for utterances that expect input
                if ("ussd_menu".equals(utteranceId) || "ussd_input".equals(utteranceId)) {
                    Log.d(TAG, "=== READY TO LISTEN ===");
                    if (sttCallback != null) {
                        sttCallback.onTTSFinished();
                    }
                } else {
                    Log.d(TAG, "Read-only content finished, no STT needed");
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS error occurred for utteranceId: " + utteranceId);
            }
        });
    }

    // KEEP: Your existing working speakMenu method
    public void speakMenu(String ussdText) {
        if (!isTTSReady) {
            Log.w(TAG, "TTS not ready yet");
            return;
        }

        List<String> menuOptions = parseMenuOptions(ussdText);

        if (menuOptions.isEmpty()) {
            Log.w(TAG, "No menu options found to speak");
            return;
        }

        String speechText = createSpeechText(menuOptions);
        Log.d(TAG, "Speaking menu: " + speechText);

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ussd_menu");
        tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, "ussd_menu");
    }

    // NEW: Method for simple text speaking (input fields and read-only)
    public void speakSimpleText(String text) {
        speakSimpleText(text, true); // Default to expecting input
    }

    public void speakSimpleText(String text, boolean expectsInput) {
        if (!isTTSReady) {
            Log.w(TAG, "TTS not ready yet");
            return;
        }

        Log.d(TAG, "Speaking simple text: " + text + " (expects input: " + expectsInput + ")");

        String utteranceId = expectsInput ? "ussd_input" : "ussd_readonly";

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
    }

    // KEEP: Your existing parsing methods
    private List<String> parseMenuOptions(String ussdText) {
        List<String> options = new ArrayList<>();

        Pattern pattern = Pattern.compile("(\\d+)\\)\\s*(.+?)(?=\\n\\d+\\)|\\nn\\s|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(ussdText);

        while (matcher.find()) {
            String number = matcher.group(1);
            String text = Objects.requireNonNull(matcher.group(2)).trim();
            text = text.replaceAll("\\s+", " ").trim();

            String option = number + ": " + text;
            options.add(option);
            Log.d(TAG, "Parsed: " + option);
        }

        return options;
    }

    private String createSpeechText(List<String> menuOptions) {
        StringBuilder speechBuilder = new StringBuilder();
        speechBuilder.append("Say the number representing the service you want.");

        for (String option : menuOptions) {
            speechBuilder.append(option).append(". ");
        }

        return speechBuilder.toString();
    }

    public boolean isReady() {
        return isTTSReady;
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TTS shutdown");
        }
    }
}