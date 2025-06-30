package com.voiceussd.prototype.services;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class InputSimulator {
    private static final String TAG = "InputSimulator";
    private AccessibilityService accessibilityService;

    // Resource IDs (keep your existing ones)
    private static final String INPUT_FIELD_ID = "com.android.phone:id/input_field";
    private static final String SEND_BUTTON_ID = "android:id/button1";
    private static final String CANCEL_BUTTON_ID = "android:id/button2";

    public InputSimulator(AccessibilityService service) {
        this.accessibilityService = service;
    }

    // KEEP: Your existing working method for menu input
    public boolean inputNumberAndSend(int number) {
        Log.d(TAG, "=== ATTEMPTING TO INPUT NUMBER: " + number);

        // Step 1: Find and fill input field
        boolean inputSuccess = inputNumber(number);
        if (!inputSuccess) {
            Log.e(TAG, "Failed to input number");
            return false;
        }

        // Step 2: Wait 2 seconds, then click SEND Button
        Log.d(TAG, "Waiting 2 seconds before sending...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean sendSuccess = clickSendButton();
            if (!sendSuccess) {
                Log.e(TAG, "Failed to click SEND Button");
            } else {
                Log.d(TAG, "=== SUCCESSFULLY SUBMITTED: " + number + " ===");
            }
        }, 2000); // 2 second delay

        return true; // Return true since input was successful, send happens after delay
    }

    // NEW: Method for real-time digit input
    // Updated inputSingleDigit method in InputSimulator class

    public boolean inputSingleDigit(int digit) {
        Log.d(TAG, "=== INPUTTING SINGLE DIGIT: " + digit + " ===");

        // Add small delay to ensure UI is ready and previous operations are complete
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            // Get current text and append the new digit
            AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
            if (rootNode == null) {
                Log.e(TAG, "No active window found for digit input");
                return;
            }

            try {
                AccessibilityNodeInfo inputField = findInputField(rootNode);
                if (inputField == null) {
                    Log.e(TAG, "Input field not found for digit input");
                    return;
                }

                // Get current text
                String currentText = "";
                CharSequence existingText = inputField.getText();
                if (existingText != null) {
                    currentText = existingText.toString();
                }

                // Append new digit
                String newText = currentText + digit;

                // Set the updated text
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
                boolean success = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

                if (success) {
                    Log.d(TAG, "Successfully added digit: " + digit + " (Full text: '" + newText + "')");
                } else {
                    Log.e(TAG, "Failed to add digit: " + digit);
                }

                inputField.recycle();
            } finally {
                rootNode.recycle();
            }

        }, 100); // 100ms delay

        return true; // Return true immediately since the actual work happens in the handler
    }

    // NEW: Method to submit the complete long input
    public boolean submitLongInput(String fullInput) {
        Log.d(TAG, "=== SUBMITTING COMPLETE LONG INPUT: " + fullInput + " ===");

        // Wait a moment, then click send
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean sendSuccess = clickSendButton();
            if (!sendSuccess) {
                Log.e(TAG, "Failed to click SEND button for long input");
            } else {
                Log.d(TAG, "=== SUCCESSFULLY SUBMITTED LONG INPUT: " + fullInput + " ===");
            }
        }, 500); // Shorter delay for long input

        return true;
    }

    // KEEP: Your existing private methods (they work perfectly)
    private boolean inputNumber(int number) {
        AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "No active window found");
            return false;
        }

        try {
            AccessibilityNodeInfo inputField = findInputField(rootNode);
            if (inputField == null) {
                Log.e(TAG, "Input field not found");
                return false;
            }

            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, String.valueOf(number));
            boolean success = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

            if (success) {
                Log.d(TAG, "Successfully input number: " + number);
            } else {
                Log.e(TAG, "ERROR: Failed to set text in input field: ");
            }

            inputField.recycle();
            return success;
        } finally {
            rootNode.recycle();
        }
    }

    private boolean clickSendButton() {
        AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "No active window found for SEND button");
            return false;
        }

        try {
            AccessibilityNodeInfo sendButton = findSendButton(rootNode);
            if (sendButton == null) {
                Log.e(TAG, "SEND button not found");
                return false;
            }

            boolean success = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            if (success) {
                Log.d(TAG, "Successfully clicked SEND button");
            } else {
                Log.e(TAG, "Failed to click SEND button");
            }

            sendButton.recycle();
            return success;
        } finally {
            rootNode.recycle();
        }
    }

    private AccessibilityNodeInfo findInputField(AccessibilityNodeInfo root) {
        return findNodeByResourceId(root, INPUT_FIELD_ID);
    }

    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo root) {
        return findNodeByResourceId(root, SEND_BUTTON_ID);
    }

    private AccessibilityNodeInfo findNodeByResourceId(AccessibilityNodeInfo node, String resourceId) {
        if (node == null) return null;

        String nodeResourceId = node.getViewIdResourceName();
        if (resourceId.equals(nodeResourceId)) {
            Log.d(TAG, "Found target node: " + resourceId);
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeByResourceId(child, resourceId);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }
}