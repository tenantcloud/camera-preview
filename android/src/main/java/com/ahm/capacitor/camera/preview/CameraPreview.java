package com.ahm.capacitor.camera.preview;

import android.Manifest;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;

import java.util.List;

@CapacitorPlugin(
        name = "CameraPreview",
        permissions = {
                @Permission(
                        alias = "camera",
                        strings = { Manifest.permission.CAMERA }
                )
        }
)
public class CameraPreview extends Plugin implements CameraActivity.CameraPreviewListener {
    private CameraActivity fragment;
    private final int containerViewId = 20;
    private String cameraStartCallID;
    private String cameraCaptureCallID;

    @PluginMethod()
    public void start(PluginCall call) {
        bridge.saveCall(call);
        cameraStartCallID = call.getCallbackId();

        if (getPermissionState("camera") != PermissionState.GRANTED) {
            requestAllPermissions(call, "cameraPermsCallback");
        } else {
            startCamera(call);
        }
    }

    @PermissionCallback
    private void cameraPermsCallback(PluginCall call) {
        if (getPermissionState("camera") == PermissionState.GRANTED) {
            startCamera(call);
        } else {
            call.reject("Permission is required start the camera");
        }
    }

    @PluginMethod
    public void flip(PluginCall call) {
        try {
            fragment.switchCamera();
            call.resolve();
        } catch (Exception e) {
            call.reject("failed to flip camera");
        }
    }

    @PluginMethod()
    public void capture(PluginCall call) {
        if(!this.hasCamera(call)){
            call.reject("Camera is not running");
            return;
        }

        bridge.saveCall(call);
        cameraCaptureCallID = call.getCallbackId();

        Integer quality = call.getInt("quality", 85);
        fragment.takePicture(0, 0, quality);
    }

    @PluginMethod()
    public void stop(final PluginCall call) {
        bridge.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);

                if (containerView != null) {
                    ((ViewGroup)getBridge().getWebView().getParent()).removeView(containerView);
                    getBridge().getWebView().setBackgroundColor(Color.WHITE);
                    FragmentManager fragmentManager = getActivity().getFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.remove(fragment);
                    fragmentTransaction.commit();
                    fragment = null;

                    call.resolve();
                } else {
                    call.reject("camera already stopped");
                }
            }
        });
    }

    @PluginMethod()
    public void getSupportedFlashModes(PluginCall call) {
        if(!this.hasCamera(call)){
            call.reject("Camera is not running");
            return;
        }

        Camera camera = fragment.getCamera();
        Camera.Parameters params = camera.getParameters();
        List<String> supportedFlashModes;
        supportedFlashModes = params.getSupportedFlashModes();
        JSONArray jsonFlashModes = new JSONArray();

//        CameraManager manager = (CameraManager) bridge.getContext().getSystemService(Context.CAMERA_SERVICE);
//        String[] cameraIds = manager.getCameraIdList();
//        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIds);
//        mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))

        if (supportedFlashModes != null) {
            for (int i=0; i<supportedFlashModes.size(); i++) {
                jsonFlashModes.put(new String(supportedFlashModes.get(i)));
            }
        }

        JSObject jsObject = new JSObject();
        jsObject.put("result", jsonFlashModes);
        call.resolve(jsObject);

    }

    @PluginMethod()
    public void setFlashMode(PluginCall call) {
        if(!this.hasCamera(call)){
            call.reject("Camera is not running");
            return;
        }

        String flashMode = call.getString("flashMode");
        if(flashMode == null || flashMode.isEmpty()) {
            call.reject("flashMode required parameter is missing");
            return;
        }

        Camera camera = fragment.getCamera();
        Camera.Parameters params = camera.getParameters();

        List<String> supportedFlashModes;
        supportedFlashModes = camera.getParameters().getSupportedFlashModes();
        if (supportedFlashModes.contains(flashMode)) {
            params.setFlashMode(flashMode);
        } else {
            call.reject("Flash mode not recognised: " + flashMode);
            return;
        }

        fragment.setCameraParameters(params);

        call.resolve();
    }

    private void startCamera(final PluginCall call) {

        String position = call.getString("position");

        if (position == null || position.isEmpty() || "rear".equals(position)) {
            position = "back";
        } else {
            position = "front";
        }

        final Integer x = call.getInt("x", 0);
        final Integer y = call.getInt("y", 0);
        final Integer width = call.getInt("width", 0);
        final Integer height = call.getInt("height", 0);
        final Integer paddingBottom = call.getInt("paddingBottom", 0);
        final Boolean toBack = call.getBoolean("toBack", false);

        fragment = new CameraActivity();
        fragment.setEventListener(this);
        fragment.defaultCamera = position;
        fragment.tapToTakePicture = false;
        fragment.dragEnabled = false;
        fragment.tapToFocus = true;
        fragment.disableExifHeaderStripping = true;
        fragment.storeToFile = false;
        fragment.toBack = toBack;

        bridge.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DisplayMetrics metrics = getBridge().getActivity().getResources().getDisplayMetrics();
                // offset
                int computedX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, metrics);
                int computedY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, y, metrics);

                // size
                int computedWidth;
                int computedHeight;
                int computedPaddingBottom;

                if(paddingBottom != 0) {
                    computedPaddingBottom = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingBottom, metrics);
                } else {
                    computedPaddingBottom = 0;
                }

                if(width != 0) {
                    computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, metrics);
                } else {
                    Display defaultDisplay = getBridge().getActivity().getWindowManager().getDefaultDisplay();
                    final Point size = new Point();
                    defaultDisplay.getSize(size);

                    computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.x, metrics);
                }

                if(height != 0) {
                    computedHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, metrics) - computedPaddingBottom;
                } else {
                    Display defaultDisplay = getBridge().getActivity().getWindowManager().getDefaultDisplay();
                    final Point size = new Point();
                    defaultDisplay.getSize(size);

                    computedHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.y, metrics) - computedPaddingBottom;
                }

                fragment.setRect(computedX, computedY, computedWidth, computedHeight);

                FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);
                if(containerView == null){
                    containerView = new FrameLayout(getActivity().getApplicationContext());
                    containerView.setId(containerViewId);

                    getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                    ((ViewGroup)getBridge().getWebView().getParent()).addView(containerView);
                    if(toBack) {
                        getBridge().getWebView().getParent().bringChildToFront(getBridge().getWebView());
                    }

                    FragmentManager fragmentManager = getBridge().getActivity().getFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.add(containerView.getId(), fragment);
                    fragmentTransaction.commit();

                    call.resolve();
                } else {
                    call.reject("camera already started");
                }
            }
        });
    }


    @Override
    protected void handleOnResume() {
        super.handleOnResume();
    }

    @Override
    public void onPictureTaken(String originalPicture) {
        JSObject jsObject = new JSObject();
        jsObject.put("value", originalPicture);
        bridge.getSavedCall(cameraCaptureCallID).resolve(jsObject);
    }

    @Override
    public void onPictureTakenError(String message) {
        bridge.getSavedCall(cameraCaptureCallID).reject(message);
    }

    @Override
    public void onFocusSet(int pointX, int pointY) {

    }

    @Override
    public void onFocusSetError(String message) {

    }

    @Override
    public void onBackButton() {

    }

    @Override
    public void onCameraStarted() {
        PluginCall pluginCall = bridge.getSavedCall(cameraStartCallID);

        System.out.println("camera started");

        if (pluginCall != null) {
            pluginCall.resolve();
        }
    }

    private boolean hasView(PluginCall call) {
        return fragment != null;
    }

    private boolean hasCamera(PluginCall call) {
        if(!this.hasView(call)){
            return false;
        }

        return fragment.getCamera() != null;
    }

}
