package org.photonvision.vision.pipe.impl;

import edu.wpi.first.math.geometry.Quaternion;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation3d;
import org.opencv.core.*;
import org.opencv.features2d.FastFeatureDetector;
import org.opencv.imgproc.Imgproc;
import org.photonvision.vision.pipe.CVPipe;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.opencv.calib3d.Calib3d.*;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.features2d.Features2d.drawKeypoints;
import static org.opencv.video.Video.calcOpticalFlowPyrLK;

public class VisualOdometryPipe extends CVPipe<Mat, Transform3d, VisualOdometryParams>{
    private static final Scalar WHITE = new Scalar(255, 255, 255);
    private static final Scalar BLUE = new Scalar(255, 0, 0);
    private static final Scalar RED = new Scalar(0, 0, 255);
    public boolean hasReset = false;

    FastFeatureDetector fast = FastFeatureDetector.create();


    MatOfByte status = new MatOfByte();

    Mat E = new Mat();
    Mat R = new Mat();
    Mat t = new Mat();

//    Mat R_f = new Mat();
//    Mat t_f = new Mat();

    Mat prevImage = new Mat();
    MatOfPoint2f prevFeatures = new MatOfPoint2f();
    MatOfPoint2f currFeatures = new MatOfPoint2f();

    List<Point> currFeaturesList, prevFeaturesList;

//    private Transform3d lastPosition = new Transform3d();

    public void release() {
        status.release();
        E.release();
        R.release();
        t.release();
        prevImage.release();
        prevFeatures.release();
        currFeatures.release();;
        currFeatures = null;
        prevFeatures = null;
    }

    @Override
    protected Transform3d process(Mat frame_mat) {
        Transform3d tf = estimate(frame_mat);

        if(keypoints != null)
            drawKeypoints(frame_mat, keypoints, frame_mat, RED);

        if(currFeaturesList != null && prevFeaturesList != null)
            for(int i = 0; i < currFeaturesList.size(); i++){
                Point curPoint = currFeaturesList.get(i);
                Point lastPoint = prevFeaturesList.get(i);

                Imgproc.circle(frame_mat, curPoint, 5, WHITE, 1);
                Imgproc.line(frame_mat, curPoint, lastPoint, BLUE, 1);
            }

        if(tf != null) {
            return tf;
        }
        return new Transform3d();
    }


    private double featureTracking(Mat prevImage, Mat currImage, MatOfPoint2f prevFeatures, MatOfPoint2f currFeatures, MatOfByte status){
        // 트래킹에 실패한 포인트들은 버린다.
        MatOfFloat err = new MatOfFloat();
        Size winSize = new Size(21, 21);
        TermCriteria termcrit = new TermCriteria(TermCriteria.COUNT+TermCriteria.EPS, 30, 0.01);

        calcOpticalFlowPyrLK(prevImage, currImage, prevFeatures, currFeatures, status, err, winSize, 3, termcrit, 0, 0.01);

        double weight = 0;
        // KLT 트래킹에 실패하거나 프레임 바깥으로 벗어난 포인트들은 버린다.
        int indexCorrection = 0;
        byte[] statusArray = status.toArray();
        Point[] currFeaturesArray = currFeatures.toArray();
        Point[] prevFeaturesArray = prevFeatures.toArray();

        currFeaturesList = new LinkedList<>(currFeatures.toList());
        prevFeaturesList = new LinkedList<>(prevFeatures.toList());

        for(int i = 0; i < statusArray.length; i++){
            Point pt = currFeaturesArray[i];

            if((statusArray[i] == 0) || (pt.x < 0) || (pt.y < 0)){
                prevFeaturesList.remove(i - indexCorrection);
                currFeaturesList.remove(i - indexCorrection);

                indexCorrection++;
            } else {
                Point before = prevFeaturesList.get(i - indexCorrection);
                Point after = currFeaturesList.get(i - indexCorrection);
                weight += (after.x - before.x) * (after.x - before.x) + (after.y - before.y)*(after.y - before.y);
//                Point before = prevFeaturesArray[i - indexCorrection];
//                weight += ((int)pt.x - before.x)*((int)pt.x - before.x) + ((int)pt.y - before.y)*((int)pt.y - before.y);
            }
        }

        // currFeatures, prevFeatures를 필터한 특징점으로 교체함
        currFeatures.fromList(currFeaturesList);
        prevFeatures.fromList(prevFeaturesList);

        if(prevFeaturesArray.length == 0)
            return 0;
        return weight / prevFeaturesArray.length;
    }

    MatOfKeyPoint keypoints;

    private MatOfPoint2f featureDetection(Mat image) {
//        System.out.println("New points!");
        keypoints = new MatOfKeyPoint();

        fast.detect(image, keypoints);

        KeyPoint[] kps = keypoints.toArray();
        ArrayList<Point> arrayOfPoints = new ArrayList<>();

        for(int i = 0; i < kps.length; i++){
            arrayOfPoints.add(kps[i].pt);
        }

        MatOfPoint2f matOfPoint = new MatOfPoint2f();
        matOfPoint.fromList(arrayOfPoints);

        return matOfPoint;
    }

    private Transform3d estimate(Mat currImage) {
        Point[] prevFeaturesArray = prevFeatures.toArray();

        if(prevImage.empty()){
            prevImage = currImage.clone();
            prevFeatures = featureDetection(prevImage);
//            System.out.println(4);
            return null;
        }  else if (prevFeaturesArray.length < params.minFeatures) {
            prevFeatures = featureDetection(prevImage);
            prevImage = currImage.clone();

            if(prevFeaturesArray.length <= 0){
                System.out.println("Can't detect features.");
            }


            System.out.println("Feature count below minimum threshhold " + prevFeaturesArray.length + " < " + params.minFeatures);

            return null;
        }

        double weight = featureTracking(prevImage, currImage, prevFeatures, currFeatures, status);

        if(prevFeaturesArray.length <= 0){
            prevFeatures = featureDetection(currImage);
            prevImage = currImage.clone();
//            System.out.println(2);
            return null;
        }

        if(weight < params.imageDifferenceThreshold) {
            prevImage = currImage.clone();
            currFeatures.copyTo(prevFeatures);

            System.out.println("Weight below minimum threshhold " + weight + " < " + params.imageDifferenceThreshold);
            return null;
        }

        try {
            E = findEssentialMat(prevFeatures, currFeatures, params.cam_mat);// params.focal, params.pp);//, RANSAC, params.essentialMatProb, params.essentialMatThreshold);
//            System.out.println(E.size());

//            double sum =0;
//            for(int x =0; x<E.width(); x++)
//                for(int y = 0; y<E.height(); y++)
//                    sum += E.get(y,x)[0];
//
//            System.out.println(sum);


//            System.out.println("npoints = " + prevFeatures.checkVector(2));
//            System.out.println("mask.checkVector(1) = " + status.checkVector(1));
            recoverPose(E, currFeatures, prevFeatures, R, t);//, params.cam_mat);// params.focal, params.pp);//, status);
        } catch (Exception e){
            prevImage = currImage.clone();
            currFeatures.copyTo(prevFeatures);
            e.printStackTrace();
            return null;
        }

//        if(R_f.empty()) {
//            R_f = R.clone();
//            t_f = t.clone();
//        } else {
////            Mat tmp = new Mat();
////            Core.gemm(R_f, t, 1, new Mat(), 0, tmp);
////            Core.add(t_f, tmp, t_f);
////            Core.multiply(R, R_f, R_f);
////            Core.gemm(R, R_f, 1, new Mat(), 0, R_f, 0);
//        }

        prevImage = currImage.clone();
        currFeatures.copyTo(prevFeatures);

//        System.out.println(R.size());

        double pos_x = t.get(0,0)[0];
        double pos_y = t.get(1,0)[0];
        double pos_z = t.get(2,0)[0];
//
        double roll  = Math.atan2( R.get(2,1)[0],R.get(2,2)[0]) * Math.PI / 180;
        double pitch = Math.atan2(-R.get(2,0)[0],Math.sqrt(Math.pow(R.get(2,1)[0],2)+Math.pow(R.get(2,2)[0],2))) * Math.PI / 180;
        double yaw   = Math.atan2 (R.get(1,0)[0],R.get(0,0)[0]) * Math.PI / 180;


//        double rot_w = Math.sqrt(1.0 + R.get(0,0)[0] + R.get(1,1)[0] + R.get(2,2)[0]) / 2.0;
//        double rot_x = (R.get(2,1)[0] - R.get(1,2)[0]) / (rot_w*4);
//        double rot_y = (R.get(0,2)[0] - R.get(2,0)[0]) / (rot_w*4);
//        double rot_z = (R.get(1,0)[0] - R.get(0,1)[0]) / (rot_w*4);

        return new Transform3d(
//                new Translation3d(),
                new Translation3d(pos_x, pos_y, pos_z),
//                new Rotation3d()
                new Rotation3d(roll, pitch, yaw)
        );
    }

    public void reset() {
//        prevImage.release();
        prevImage = new Mat();
        System.out.println("Reset");
    }
}
