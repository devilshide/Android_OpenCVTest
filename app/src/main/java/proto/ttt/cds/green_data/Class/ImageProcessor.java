package proto.ttt.cds.green_data.Class;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;

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

    private Mat mRGB, mTempMat;

    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV Loaded");
        } else {
            Log.d(TAG, "OpenCV NOT Loaded");
        }
    }

    public ImageProcessor() {}

    public double[][] getBiggestContoursFromImg(String imgPath, Rect[] subAreaRect, Rect wholeRect,
                                                int numOfContour) {
        Bitmap bitmap = getRGBBitmap(imgPath);
        if (bitmap != null) {
            Mat inputMat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC3);
            Utils.bitmapToMat(bitmap, inputMat);

            return getBiggestContours(inputMat, subAreaRect, wholeRect, numOfContour);
        }
        Log.d(TAG, "getBiggestContoursFromImg(): No Bitmap found from path = " + imgPath);
        return null;
    }

    /**
     * @param rgbMat Mat value of an image in RGB format
     * @param subs This can be null if there are no sub areas. This will search contours within the
     *                 entire image
     * @param origArea The area where sub areas were measured
     * @param numOfContour number of biggest contours to search inside each area
     * @return The area for the biggest contours in the biggest order for each sub areas
     *
    */
    private double[][] getBiggestContours(Mat rgbMat, Rect[] subs, Rect origArea, int numOfContour) {
        if (rgbMat == null || numOfContour < 1 || (subs != null && subs.length == 0)) {
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

        Rect[] subAreaRect = reCalcSubArea(subs, origArea, new Rect(0, 0, masked.cols(), masked.rows()));

        List<MatOfPoint> contourPoints = new ArrayList<MatOfPoint>();

        // Save the biggest contours for each individual sub area
        int subAreasCnt = subAreaRect == null ? 1 : subAreaRect.length;
        double[][] biggestAreas = new double[numOfContour][subAreasCnt];
        for (int i = 0; i < subAreasCnt; i++) {
            contourPoints.clear();

            if (subAreaRect == null) {
                // Find contours from the entire image
                Imgproc.findContours(masked, contourPoints, new Mat(), Imgproc.RETR_EXTERNAL
                        , Imgproc.CHAIN_APPROX_SIMPLE);
            } else {
                // Find contours from each sub areas
                if (subAreaRect[i].top < 0 || subAreaRect[i].bottom > masked.rows()
                        || subAreaRect[i].left < 0 || subAreaRect[i].right > masked.cols()) {
                    Log.d(TAG, "getBiggestContours(): Wrong subArea bounds, subAreaRect[" + i +
                            "] = " + subAreaRect[i] + "\nmasked(rows, cols) = (" + masked.rows()
                            + ", " + masked.cols() + ")");
                    return null;
                }
                Imgproc.findContours(
                        masked.submat(subAreaRect[i].top, subAreaRect[i].bottom
                                , subAreaRect[i].left, subAreaRect[i].right)
                        , contourPoints, new Mat()
                        , Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            }

            // Save biggest contours in decremental order
            Iterator<MatOfPoint> each = contourPoints.iterator();
            while (each.hasNext()) {
                MatOfPoint next = each.next();
                double area = Imgproc.contourArea(next);
                for (int order = 0; order < numOfContour; order++) {
                    if (area > biggestAreas[order][i]) {
                        if (order != numOfContour - 1) {
                            biggestAreas[order + 1][i] = biggestAreas[order][i];
                            biggestAreas[order][i] = area;
                        } else {
                            biggestAreas[order][i] = area;
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

    private Bitmap getRGBBitmap(String path) {
        if (path != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            Bitmap bitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            return bitmap32;
        }
        Log.d(TAG, "getRGBBitmap(): File path is NULL");
        return null;
    }

    /**
     * Recalculates all sub areas to fit the size of the new area
     */
    private Rect[] reCalcSubArea(Rect[] subAreas, Rect area, Rect newArea) {
        if (area.equals(newArea)) {
            return subAreas;
        }
        Rect[] newSubArea = new Rect[subAreas.length];
        double topRatio, bottomRatio, leftRatio, rightRatio;
        for (int i = 0; i < subAreas.length; i++) {
            Rect subRect = subAreas[i];
            if (area.contains(subRect)) {
                topRatio = bottomRatio = leftRatio = rightRatio = 0.0;
                if (subRect.top != area.top) {
                    topRatio = (double)(subRect.top - area.top) / area.height();
                }
                if (subRect.bottom != area.bottom) {
                    bottomRatio = (double)(area.bottom - subRect.bottom) / area.height();
                }
                if (subRect.left != area.left) {
                    leftRatio = (double)(subRect.left - area.left) / area.width();
                }
                if (subRect.right != area.right) {
                    rightRatio = (double)(area.right - subRect.right) / area.width();
                }

                int newTop = (int)(newArea.top + (newArea.height() * topRatio));
                int newBottom = (int)(newArea.bottom - (newArea.height() * bottomRatio));
                int newLeft = (int)(newArea.left + (newArea.width() * leftRatio));
                int newRight = (int)(newArea.right - (newArea.width() * rightRatio));
                newSubArea[i] = new Rect(newLeft, newTop, newRight, newBottom);
            }
        }
        return newSubArea;
    }

}
