import Foundation
import Capacitor
import AVFoundation
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(CameraPreview)
public class CameraPreview: CAPPlugin {

    var previewView: UIView!
    var cameraPosition = String()
    let cameraController = CameraController()
    var width: CGFloat = UIScreen.main.bounds.size.width
    var height: CGFloat = UIScreen.main.bounds.size.height
    var x: CGFloat = 0
    var y: CGFloat = 0
    var paddingBottom: CGFloat?
    var rotateWhenOrientationChanged: Bool?

    @objc func rotated() {

        var height = self.height

        if let paddingBottom = self.paddingBottom {
            height = self.height - paddingBottom
        }

        if UIDevice.current.orientation.isLandscape {

            self.previewView.frame = CGRect(x: self.x, y: self.y, width: height, height: self.width)
            self.cameraController.previewLayer?.frame = self.previewView.frame

            if (UIDevice.current.orientation == UIDeviceOrientation.landscapeLeft) {
                self.cameraController.previewLayer?.connection?.videoOrientation = .landscapeRight
            }

            if (UIDevice.current.orientation == UIDeviceOrientation.landscapeRight) {
                self.cameraController.previewLayer?.connection?.videoOrientation = .landscapeLeft
            }
        }

        if UIDevice.current.orientation.isPortrait {
            self.previewView.frame = CGRect(x: self.x, y: self.y, width: self.width, height: height)
            self.cameraController.previewLayer?.frame = self.previewView.frame
            self.cameraController.previewLayer?.connection?.videoOrientation = .portrait
        }
    }

    @objc func start(_ call: CAPPluginCall) {
        self.cameraPosition = call.getString("position") ?? "rear"

        self.x = CGFloat(call.getInt("x", 0)) / UIScreen.main.scale
        self.y = CGFloat(call.getInt("y", 0)) / UIScreen.main.scale

        self.width = CGFloat(call.getFloat("width", Float(UIScreen.main.bounds.size.width)))
        self.height = CGFloat(call.getFloat("height", Float(UIScreen.main.bounds.size.height)))

        if let paddingBottom = call.getInt("paddingBottom") {
            self.paddingBottom = CGFloat(paddingBottom)
        }

        self.rotateWhenOrientationChanged = call.getBool("rotateWhenOrientationChanged") ?? true

        if (self.rotateWhenOrientationChanged == true) {
            NotificationCenter.default.addObserver(self, selector: #selector(CameraPreview.rotated), name: UIDevice.orientationDidChangeNotification, object: nil)
        }

        DispatchQueue.main.async {
            if (self.cameraController.captureSession?.isRunning ?? false) {
                call.reject("camera already started")
            } else {
                self.cameraController.prepare(cameraPosition: self.cameraPosition){error in
                    if let error = error {
                        print(error)
                        call.reject(error.localizedDescription)
                        return
                    }

                    guard let webView = self.webView else {
                        call.reject("Error. Can't get webView")
                        return
                    }

                    self.previewView = UIView(frame: CGRect(x: self.x, y: self.y, width: self.width, height: self.height))

                    webView.isOpaque = false
                    webView.backgroundColor = UIColor.clear
                    webView.superview?.addSubview(self.previewView)
                    webView.superview?.bringSubviewToFront(webView)

                    try? self.cameraController.displayPreview(on: self.previewView)

                    call.resolve()

                }
            }
        }
    }

    @objc func flip(_ call: CAPPluginCall) {
        do {
            try self.cameraController.switchCameras()
            call.resolve()
        } catch {
            call.reject("failed to flip camera")
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.cameraController.captureSession?.stopRunning()
            self.previewView.removeFromSuperview()

            guard let webView = self.webView else {
                call.reject("Error. Can't get webView")
                return
            }

            webView.isOpaque = true

            call.resolve()
        }
    }

    @objc func capture(_ call: CAPPluginCall) {
        let quality: Int = call.getInt("quality", 85)

        self.cameraController.captureImage { (image, error) in

            guard let image = image else {
                print(error ?? "Image capture error")

                guard let error = error else {
                    call.reject("Image capture error")
                    return
                }

                call.reject(error.localizedDescription)

                return
            }

            guard let imageData = image.jpegData(compressionQuality: CGFloat(quality)) else {
                call.reject("Can't get image data")

                return
            }

            let imageBase64 = imageData.base64EncodedString()

            call.resolve(["value": imageBase64])
        }
    }

    @objc func getSupportedFlashModes(_ call: CAPPluginCall) {
        do {
            let supportedFlashModes = try self.cameraController.getSupportedFlashModes()

            call.resolve(["result": supportedFlashModes])
        } catch {
            call.reject("failed to get supported flash modes")
        }
    }

    @objc func setFlashMode(_ call: CAPPluginCall) {
        guard let flashMode = call.getString("flashMode") else {
            call.reject("failed to set flash mode. required parameter flashMode is missing")

            return
        }
        do {
            var flashModeAsEnum: AVCaptureDevice.FlashMode?

            switch flashMode {
            case "off" :
                flashModeAsEnum = AVCaptureDevice.FlashMode.off
            case "on":
                flashModeAsEnum = AVCaptureDevice.FlashMode.on
            case "auto":
                flashModeAsEnum = AVCaptureDevice.FlashMode.auto
            default: break;
            }

            guard let flashModeEnum = flashModeAsEnum else {
                if(flashMode == "torch") {
                    try self.cameraController.setTorchMode()

                    call.resolve()
                    return
                }

                call.reject("Flash Mode not supported")
                return
            }

            try self.cameraController.setFlashMode(flashMode: flashModeEnum)
            call.resolve()

        } catch {
            call.reject("failed to set flash mode")
        }
    }
    
}
