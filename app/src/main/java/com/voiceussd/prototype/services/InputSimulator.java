package com.voiceussd.prototype.services;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Objects;

public class InputSimulator{
    private static final String TAG = "InputSimulator";
    private AccessibilityService accessibilityService;

    // Resource IDs
    private static final String INPUT_FIELD_ID = "com.android.phone:id/input_field";
    private static final String SEND_BUTTON_ID = "android:id/button1";
    private static final String CANCEL_BUTTON_ID = "android:id/button2";

    public InputSimulator(AccessibilityService service){
        this.accessibilityService = service;
    }

    public boolean inputNumberAndSend(int number){
        Log.d(TAG, "=== ATTEMPTING TO INPUT NUMBER: " + number);

        // Step 1: Find and fill input field
        boolean inputSuccess = inputNumber(number);
        if(!inputSuccess){
            Log.e(TAG, "Failed to input number");
            return false;
        }

        //Step 2: Click SEND Button
        boolean sendSuccess = clickSendButton();
        if(!sendSuccess){
            Log.e(TAG, "Failed to click SEND Button");
            return false;
        }

        Log.d(TAG, "=== SUCCESSFULLY SUBMITTED: "+ number + " ===");
        return true;
    }

    private boolean inputNumber(int number){
        // Access the window structure
        AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
        if(rootNode == null){
            Log.e(TAG, "No active window found");
            return false;
        }

        try{
            // Access input field
            AccessibilityNodeInfo inputField = findInputField(rootNode);
            if(inputField == null){
                Log.e(TAG, "Input field not found");
                return false;
            }

            // Set text in input field
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, String.valueOf(number));
            boolean success = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

            if(success){
                Log.d(TAG, "Successfully input number: "+ number);
            }else{
                Log.e(TAG, "ERROR: Failed to set text in input field: ");
            }

            // Just free memory
            inputField.recycle(); // Note: this doesn't empty text field UI
            return success;
        }finally{
            rootNode.recycle();
        }
    }

    private boolean clickSendButton(){
        AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
        if(rootNode == null){
            Log.e(TAG, "No active window found for SEND button");
            return false;
        }

        try{
            AccessibilityNodeInfo sendButton = findSendButton(rootNode);
            if(sendButton == null){
                Log.e(TAG, "SEND button not found");
                return false;
            }

            boolean success = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            if (success){
                Log.d(TAG, "Successfully clicked SEND button");
            }else{
                Log.e(TAG, "Failed to click SEND button");
            }

            sendButton.recycle();
            return success;
        }finally{
            rootNode.recycle();
        }
    }
    private AccessibilityNodeInfo findInputField(AccessibilityNodeInfo root){
        return findNodeByResourceId(root, INPUT_FIELD_ID);
    }

    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo root){
        return findNodeByResourceId(root, SEND_BUTTON_ID);
    }

    private AccessibilityNodeInfo findNodeByResourceId(AccessibilityNodeInfo node, String resourceId){
        if(node == null) return null;

        // Check current node
        String nodeResourceId = node.getViewIdResourceName(); // Note: all AccessibilityNodeInfo objs have getViewIdResourceName method
        if(resourceId.equals(nodeResourceId)){
            Log.d(TAG, "Found target node: "+ resourceId);
            return node;
        }

        // Search children recursively
        for(int i = 0; i < node.getChildCount(); i ++){
            AccessibilityNodeInfo child = node.getChild(i);
            if(child != null){
                AccessibilityNodeInfo result = findNodeByResourceId(child, resourceId);
                if(result != null){
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    // Utility methods for testing / debugging
    public boolean cancelUSSD(){
        // NOTE: Method Not necessary now
        return false;
    }

    public boolean isInputFieldAvailable(){
        // NOTES: Method Not necessary now
        return false;
    }


}