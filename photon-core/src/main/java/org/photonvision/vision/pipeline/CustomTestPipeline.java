package org.photonvision.vision.pipeline;

import edu.wpi.first.apriltag.AprilTagDetection;
import edu.wpi.first.apriltag.AprilTagDetector;
import edu.wpi.first.apriltag.AprilTagPoseEstimate;
import edu.wpi.first.apriltag.AprilTagPoseEstimator;
import edu.wpi.first.math.geometry.*;
import edu.wpi.first.math.util.Units;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.photonvision.common.configuration.ConfigManager;
import org.photonvision.common.util.ColorHelper;
import org.photonvision.common.util.math.MathUtils;
import org.photonvision.estimation.TargetModel;
import org.photonvision.targeting.MultiTargetPNPResult;
import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.frame.Frame;
import org.photonvision.vision.frame.FrameThresholdType;
import org.photonvision.vision.pipe.CVPipe;
import org.photonvision.vision.pipe.impl.*;
import org.photonvision.vision.pipeline.result.CVPipelineResult;
import org.photonvision.vision.target.TrackedTarget;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.opencv.features2d.Features2d.drawKeypoints;
import static org.opencv.video.Video.calcOpticalFlowPyrLK;

public class CustomTestPipeline extends CVPipeline<CVPipelineResult, CustomTestPipelineSettings> {
    private static final FrameThresholdType PROCESSING_TYPE = FrameThresholdType.GREYSCALE;
    private final CalculateFPSPipe calculateFPSPipe = new CalculateFPSPipe();

    private final AprilTagDetectionPipe aprilTagDetectionPipe = new AprilTagDetectionPipe();
    private final AprilTagPoseEstimatorPipe singleTagPoseEstimatorPipe = new AprilTagPoseEstimatorPipe();
    private final MultiTargetPNPPipe multiTagPNPPipe = new MultiTargetPNPPipe();

    private final VisualOdometryPipe visualOdometryPipe = new VisualOdometryPipe();

    private List<TrackedTarget> previousAprilTags;

    Mat cam_mat;
    public CustomTestPipeline() {
        super(PROCESSING_TYPE);
        settings = new CustomTestPipelineSettings();
    }

    public CustomTestPipeline(CustomTestPipelineSettings settings) {
        super(PROCESSING_TYPE);
        this.settings = settings;
    }

    @Override
    protected void setPipeParamsImpl() {

        VisualOdometryParams VOConfig = new VisualOdometryParams();
        VOConfig.featureThreshold = settings.featureThreshold;
        VOConfig.minFeatures = settings.minFeatures;
        VOConfig.imageDifferenceThreshold = settings.imageDifferenceThreshold;
        VOConfig.essentialMatProb = settings.essentialMatProb;
        VOConfig.essentialMatThreshold = settings.essentialMatThreshold;

        if(frameStaticProperties.cameraCalibration != null) {
            cam_mat = frameStaticProperties.cameraCalibration.getCameraIntrinsicsMat();

            double fx = cam_mat.get(0,0)[0];
            double fy = cam_mat.get(1,1)[0];

            double x = cam_mat.get(0,2)[0];
            double y = cam_mat.get(1,2)[0];

            double width = frameStaticProperties.imageWidth;
            double height = frameStaticProperties.imageHeight;

            VOConfig.cam_mat = cam_mat;

//            VOConfig.focal = 2;
//            VOConfig.pp.x = x;
//            VOConfig.pp.y = y;
        }

        visualOdometryPipe.setParams(VOConfig);





        // Sanitize thread count - not supported to have fewer than 1 threads
        settings.threads = Math.max(1, settings.threads);

        // for now, hard code tag width based on enum value
        // 2023/other: best guess is 6in
        double tagWidth = Units.inchesToMeters(6);
        TargetModel tagModel = TargetModel.kAprilTag16h5;
        if (settings.tagFamily == AprilTagFamily.kTag36h11) {
            // 2024 tag, 6.5in
            tagWidth = Units.inchesToMeters(6.5);
            tagModel = TargetModel.kAprilTag36h11;
        }

        var config = new AprilTagDetector.Config();
        config.numThreads = settings.threads;
        config.refineEdges = settings.refineEdges;
        config.quadSigma = (float) settings.blur;
        config.quadDecimate = settings.decimate;

        var quadParams = new AprilTagDetector.QuadThresholdParameters();
        // 5 was the default minClusterPixels in WPILib prior to 2025
        // increasing it causes detection problems when decimate > 1
        quadParams.minClusterPixels = 5;
        // these are the same as the values in WPILib 2025
        // setting them here to prevent upstream changes from changing behavior of the detector
        quadParams.maxNumMaxima = 10;
        quadParams.criticalAngle = 45 * Math.PI / 180.0;
        quadParams.maxLineFitMSE = 10.0f;
        quadParams.minWhiteBlackDiff = 5;
        quadParams.deglitch = false;

        aprilTagDetectionPipe.setParams(
                new AprilTagDetectionPipe.AprilTagDetectionPipeParams(settings.tagFamily, config, quadParams));

        if (frameStaticProperties.cameraCalibration != null) {
            var cameraMatrix = frameStaticProperties.cameraCalibration.getCameraIntrinsicsMat();
            if (cameraMatrix != null && cameraMatrix.rows() > 0) {
                var cx = cameraMatrix.get(0, 2)[0];
                var cy = cameraMatrix.get(1, 2)[0];
                var fx = cameraMatrix.get(0, 0)[0];
                var fy = cameraMatrix.get(1, 1)[0];

                singleTagPoseEstimatorPipe.setParams(
                        new AprilTagPoseEstimatorPipe.AprilTagPoseEstimatorPipeParams(
                                new AprilTagPoseEstimator.Config(tagWidth, fx, fy, cx, cy),
                                frameStaticProperties.cameraCalibration,
                                settings.numIterations));

                // TODO global state ew
                var atfl = ConfigManager.getInstance().getConfig().getApriltagFieldLayout();
                multiTagPNPPipe.setParams(
                        new MultiTargetPNPPipe.MultiTargetPNPPipeParams(frameStaticProperties.cameraCalibration, atfl, tagModel));
            }
        }
    }


    private static final Scalar FONT_COLOR = ColorHelper.colorToScalar(Color.RED);

    @Override
    protected CVPipelineResult process(Frame frame, CustomTestPipelineSettings settings) {
        long sumPipeNanosElapsed = 0L;

        if (frame.type != FrameThresholdType.GREYSCALE) {
            // We asked for a GREYSCALE frame, but didn't get one -- best we can do is give up
            Imgproc.putText(frame.processedImage.getMat(), "Not Greyscale", new Point(10,50), Imgproc.FONT_HERSHEY_TRIPLEX, 1, FONT_COLOR);
            return new CVPipelineResult(frame.sequenceID, 0, 0, List.of(), frame);
        }

        if(frameStaticProperties.cameraCalibration == null) {
            // The camera must be calibrated
            Imgproc.putText(frame.processedImage.getMat(), "Not Calibrated", new Point(10,50), Imgproc.FONT_HERSHEY_TRIPLEX, 1, FONT_COLOR);
            return new CVPipelineResult(frame.sequenceID, 0, 0, List.of(), frame);
        }


//        Transform3d tf = visualOdometryPipe.run(frame.processedImage.getMat()).output;
//
//        var fps = calculateFPSPipe.run(null).output;
//
//        List<TrackedTarget> result = new ArrayList<>();
//
//        TrackedTarget target = new TrackedTarget();
//        target.setBestCameraToTarget3d(tf);
////        target.tran
//
//        result.add(target);
//
//        return new CVPipelineResult(frame.sequenceID, total_proc_time, fps, result, frame);

        CVPipe.CVPipeResult<List<AprilTagDetection>> tagDetectionPipeResult;
        tagDetectionPipeResult = aprilTagDetectionPipe.run(frame.processedImage);
        sumPipeNanosElapsed += tagDetectionPipeResult.nanosElapsed;

        List<AprilTagDetection> detections = tagDetectionPipeResult.output;
        List<AprilTagDetection> usedDetections = new ArrayList<>();
        List<TrackedTarget> targetList = new ArrayList<>();

        // Filter out detections based on pipeline settings
        for (AprilTagDetection detection : detections) {
            // TODO this should be in a pipe, not in the top level here (Matt)
            if (detection.getDecisionMargin() < settings.decisionMargin) continue;
            if (detection.getHamming() > settings.hammingDist) continue;

            usedDetections.add(detection);

            // Populate target list for multitag
            // (TODO: Address circular dependencies. Multitag only requires corners and IDs, this should
            // not be necessary.)
            TrackedTarget target =
                    new TrackedTarget(
                            detection,
                            null,
                            new TrackedTarget.TargetCalculationParameters(
                                    false, null, null, null, null, frameStaticProperties));

            targetList.add(target);
        }

        // Do multi-tag pose estimation
        Optional<MultiTargetPNPResult> multiTagResult = Optional.empty();
        if (settings.solvePNPEnabled && settings.doMultiTarget) {
            var multiTagOutput = multiTagPNPPipe.run(targetList);
            sumPipeNanosElapsed += multiTagOutput.nanosElapsed;
            multiTagResult = multiTagOutput.output;
        }

        // Do single-tag pose estimation
        if (settings.solvePNPEnabled) {
            // Clear target list that was used for multitag so we can add target transforms
            targetList.clear();
            // TODO global state again ew
            var atfl = ConfigManager.getInstance().getConfig().getApriltagFieldLayout();

            for (AprilTagDetection detection : usedDetections) {
                AprilTagPoseEstimate tagPoseEstimate = null;
                // Do single-tag estimation when "always enabled" or if a tag was not used for multitag
                if (settings.doSingleTargetAlways
                        || !(multiTagResult.isPresent()
                        && multiTagResult.get().fiducialIDsUsed.contains((short) detection.getId()))) {
                    var poseResult = singleTagPoseEstimatorPipe.run(detection);
                    sumPipeNanosElapsed += poseResult.nanosElapsed;
                    tagPoseEstimate = poseResult.output;
                }

                // If single-tag estimation was not done, this is a multi-target tag from the layout
                if (tagPoseEstimate == null && multiTagResult.isPresent()) {
                    // compute this tag's camera-to-tag transform using the multitag result
                    var tagPose = atfl.getTagPose(detection.getId());
                    if (tagPose.isPresent()) {
                        var camToTag =
                                new Transform3d(
                                        new Pose3d().plus(multiTagResult.get().estimatedPose.best), tagPose.get());
                        // match expected AprilTag coordinate system
                        camToTag =
                                CoordinateSystem.convert(camToTag, CoordinateSystem.NWU(), CoordinateSystem.EDN());
                        // (AprilTag expects Z axis going into tag)
                        camToTag =
                                new Transform3d(
                                        camToTag.getTranslation(),
                                        new Rotation3d(0, Math.PI, 0).plus(camToTag.getRotation()));
                        tagPoseEstimate = new AprilTagPoseEstimate(camToTag, camToTag, 0, 0);
                    }
                }

                // populate the target list
                // Challenge here is that TrackedTarget functions with OpenCV Contour
                TrackedTarget target =
                        new TrackedTarget(
                                detection,
                                tagPoseEstimate,
                                new TrackedTarget.TargetCalculationParameters(
                                        false, null, null, null, null, frameStaticProperties));

                var correctedBestPose =
                        MathUtils.convertOpenCVtoPhotonTransform(target.getBestCameraToTarget3d());
                var correctedAltPose =
                        MathUtils.convertOpenCVtoPhotonTransform(target.getAltCameraToTarget3d());

                target.setBestCameraToTarget3d(
                        new Transform3d(correctedBestPose.getTranslation(), correctedBestPose.getRotation()));
                target.setAltCameraToTarget3d(
                        new Transform3d(correctedAltPose.getTranslation(), correctedAltPose.getRotation()));

                targetList.add(target);
            }
        }

//        if(!targetList.isEmpty()) {
//            previousAprilTags = targetList;
//            visualOdometryPipe.hasReset = false;
//        } else if(previousAprilTags != null) {
//            if(!visualOdometryPipe.hasReset){
//                visualOdometryPipe.reset();
//                visualOdometryPipe.hasReset = true;
//            }

            CVPipe.CVPipeResult<Transform3d> VOResult = visualOdometryPipe.run(frame.processedImage.getMat());
            sumPipeNanosElapsed += VOResult.nanosElapsed;

            double x = VOResult.output.getX();
            double y = VOResult.output.getY();

            double ang = Math.atan2(y,x);
            double mag = Math.sqrt(x*x+y*y);

            System.out.println("Offset X: " + x + " Y: " + y);
            System.out.println("Offset A: " + ang + " M: " + mag);
//            System.out.println("X: " + VOResult.output.getTranslation());

//            for(int i = 0; i < previousAprilTags.size(); i++){
//                TrackedTarget old = previousAprilTags.get(i);
//                old.setBestCameraToTarget3d(old.getBestCameraToTarget3d().plus(VOResult.output));
//
//            }

//        }

        var fpsResult = calculateFPSPipe.run(null);
        var fps = fpsResult.output;

        return new CVPipelineResult(
                frame.sequenceID, sumPipeNanosElapsed, fps, previousAprilTags, frame);

    }


    @Override
    public void release() {
        aprilTagDetectionPipe.release();
        singleTagPoseEstimatorPipe.release();
        visualOdometryPipe.release();
        super.release();
    }
}