package org.photonvision.vision.pipe.impl;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.photonvision.vision.pipeline.CustomTestPipelineSettings;
import org.photonvision.vision.pipeline.PipelineType;

public class VisualOdometryParams {
    public int featureThreshold = 1;
    public int minFeatures = 500;
    public int imageDifferenceThreshold = 150;
    public double essentialMatProb = 0.999;
    public double essentialMatThreshold = 1.;
    public double focal = 1.;
    public Point pp = new Point();
    public Mat cam_mat = new Mat();

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

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        VisualOdometryParams other = (VisualOdometryParams) obj;
        if(featureThreshold != other.featureThreshold) return false;
        if(minFeatures != other.minFeatures) return false;
        if(essentialMatProb != other.essentialMatProb) return false;
        if(essentialMatThreshold != other.essentialMatThreshold) return false;
        return true;
    }
}