package org.photonvision.vision.pipeline;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.target.TargetModel;

@JsonTypeName("CustomTestPipelineSettings")
public class CustomTestPipelineSettings extends AdvancedPipelineSettings {
    public int featureThreshold = 1;
    public int minFeatures = 500;
    public int imageDifferenceThreshold = 150;
    public double essentialMatProb = 0.999;
    public double essentialMatThreshold = 1.;

    public AprilTagFamily tagFamily = AprilTagFamily.kTag36h11;
    public int decimate = 1;
    public double blur = 0;
    public int threads = 4; // Multiple threads seems to be better performance on most platforms
    public boolean debug = false;
    public boolean refineEdges = true;
    public int numIterations = 40;
    public int hammingDist = 0;
    public int decisionMargin = 35;
    public boolean doMultiTarget = false;
    public boolean doSingleTargetAlways = false;


    public CustomTestPipelineSettings() {
        super();
        pipelineType = PipelineType.CustomTest;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        long temp;

        temp = Double.doubleToLongBits(featureThreshold);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minFeatures);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(imageDifferenceThreshold);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(essentialMatProb);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(essentialMatThreshold);
        result = prime * result + (int) (temp ^ (temp >>> 32));

        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + threads;
        result = prime * result + (debug ? 1231 : 1237);
        result = prime * result + (refineEdges ? 1231 : 1237);
        result = prime * result + numIterations;
        result = prime * result + hammingDist;
        result = prime * result + decisionMargin;
        result = prime * result + (doMultiTarget ? 1231 : 1237);
        result = prime * result + (doSingleTargetAlways ? 1231 : 1237);

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        CustomTestPipelineSettings other = (CustomTestPipelineSettings) obj;
        if(featureThreshold != other.featureThreshold) return false;
        if(minFeatures != other.minFeatures) return false;
        if(essentialMatProb != other.essentialMatProb) return false;
        if(essentialMatThreshold != other.essentialMatThreshold) return false;

        if (tagFamily != other.tagFamily) return false;
        if (decimate != other.decimate) return false;
        if (Double.doubleToLongBits(blur) != Double.doubleToLongBits(other.blur)) return false;
        if (threads != other.threads) return false;
        if (debug != other.debug) return false;
        if (refineEdges != other.refineEdges) return false;
        if (numIterations != other.numIterations) return false;
        if (hammingDist != other.hammingDist) return false;
        if (decisionMargin != other.decisionMargin) return false;
        if (doMultiTarget != other.doMultiTarget) return false;
        if (doSingleTargetAlways != other.doSingleTargetAlways) return false;

        return true;
    }
}