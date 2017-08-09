package proto.ttt.cds.opencvtest_300_2.Class;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by changdo on 17. 8. 3.
 */

public class ImageProcessor {
    public static final String TAG = "ImageProcessor";

    private static final boolean DEBUG_IMAGE_PROCESSOR = false;

    public static final int MAX_CONTOUR = 0;

    private String mFilePath;
    private Mat mRGB, mTempMat;

    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV Loaded");
        } else {
            Log.d(TAG, "OpenCV NOT Loaded");
        }
    }

    public ImageProcessor() {}

    public ImageProcessor(String filePath) {
        mFilePath = filePath;
    }

    public double[][] getBiggestContoursFromCamera(CameraBridgeViewBase.CvCameraViewFrame inputFrame
            , Rect[] subSection, int areaCnt) {
        return getBiggestContours(inputFrame.rgba(), subSection, areaCnt);
    }

    public double[][] getBiggestContoursFromImg(Rect[] subAreaRect, int numOfContour) {
        Bitmap bitmap = getRGBBitmap();
        if (bitmap != null) {
            Mat inputMat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC3);
            Utils.bitmapToMat(bitmap, inputMat);

            return getBiggestContours(inputMat, subAreaRect, numOfContour);
        }
        Log.d(TAG, "getBiggestContoursFromImg(): No contours found");
        return null;
    }

    /**
     * @param rgbMat Mat value of an image in RGB format
     * @param subAreaRect This can be null if there are no sub areas. This will search contours within the
     *                 entire image
     * @param numOfContour number of biggest contours to search inside each area
     * @return The area for the biggest contours in the biggest order for each sub areas
     *
    */
    private double[][] getBiggestContours(Mat rgbMat, Rect[] subAreaRect, int numOfContour) {
        if (rgbMat == null || numOfContour < 1 || (subAreaRect != null && subAreaRect.length == 0)) {
            return null;
        }

        if (mRGB == null) {
            mRGB = new Mat();
        }
        if (mTempMat == null) {
            mTempMat = new Mat();
        }

        rgbMat.copyTo(mRGB);
        Imgproc.cvtColor(mRGB, mTempMat, Imgproc.COLOR_RGB2HSV);

        Mat masked = new Mat();
        Scalar green_l = new Scalar(30, 50, 50);
        Scalar green_u = new Scalar(90, 255, 255);

        Core.inRange(mTempMat, green_l, green_u, masked);
        Imgproc.dilate(masked, masked, Imgproc.getStructuringElement(Imgproc.MORPH_DILATE, new Size(15,15)));

        List<MatOfPoint> contourPoints = new ArrayList<MatOfPoint>();

        int subAreasCnt = subAreaRect == null ? 1 : subAreaRect.length;
        // creates an array to store a list of biggest contours for each individual sub area
        double[][] biggestAreas = new double[numOfContour][subAreasCnt];
        for (int i = 0 ; i < numOfContour; i++) {
            for (int j = 0 ; j < subAreasCnt; j++) {
                biggestAreas[i][j] = 0;
            }
        }

        for (int i = 0; i < subAreasCnt; i++) {
            contourPoints.clear();
            if (subAreaRect == null) {
                Imgproc.findContours(masked, contourPoints, new Mat(), Imgproc.RETR_EXTERNAL
                        , Imgproc.CHAIN_APPROX_SIMPLE);
            } else {
                Imgproc.findContours(
                        masked.submat(subAreaRect[i].top, subAreaRect[i].bottom
                                , subAreaRect[i].left, subAreaRect[i].right)
                        , contourPoints, new Mat()
                        , Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            }

            double smallest_x = 0, smallest_y = 0, largest_x = 0, largest_y = 0;
            Iterator<MatOfPoint> each = contourPoints.iterator();
            while (each.hasNext()) {
                MatOfPoint next = each.next();
                double area = Imgproc.contourArea(next);
                for (int maxOrder = 0; maxOrder < numOfContour; maxOrder++) {
                    if (area > biggestAreas[maxOrder][i]) {
                        if (maxOrder != numOfContour - 1) {
                            biggestAreas[maxOrder + 1][i] = biggestAreas[maxOrder][i];
                            biggestAreas[maxOrder][i] = area;
                        } else {
                            biggestAreas[maxOrder][i] = area;
                        }
                        break;
                    }
                }
            }

            // Logging
            if (DEBUG_IMAGE_PROCESSOR) {
                char sectorChar = i == 0 ? 'A' : (i == 1 ? 'B' : 'C');
                Log.d(TAG, "[" + sectorChar + "]" + " onCameraFrame: maxArea = " + biggestAreas[MAX_CONTOUR][i]);
            }
        }

        return biggestAreas;
    }

    private Bitmap getRGBBitmap() {
        if (mFilePath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(mFilePath);
            Bitmap bitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            return bitmap32;
        }
        Log.d(TAG, "getRGBBitmap(): File path not set");
        return null;
    }


    public void setFilePath(String path) {
        mFilePath = path;
    }
}
