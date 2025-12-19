import type {ConfigurablePipelineSettings, PipelineSettings} from "@/types/PipelineTypes.ts";
import {AprilTagFamily, DefaultPipelineSettings, PipelineType, TargetModel} from "@/types/PipelineTypes.ts";

export interface CustomTestPipelineSettings extends PipelineSettings {
    pipelineType: PipelineType.CustomTest;

    featureThreshold: number
    minFeatures: number,
    imageDifferenceThreshold: number,
    essentialMatProb: number
    essentialMatThreshold: number,

    hammingDist: number;
    numIterations: number;
    decimate: number;
    blur: number;
    decisionMargin: number;
    refineEdges: boolean;
    debug: boolean;
    threads: number;
    tagFamily: AprilTagFamily;
    doMultiTarget: boolean;
    doSingleTargetAlways: boolean;

}
export type ConfigurableCustomTestPipelineSettings = Partial<
    Omit<CustomTestPipelineSettings, "pipelineType">
> &
    ConfigurablePipelineSettings;
export const DefaultCustomTestPipelineSettings: CustomTestPipelineSettings = {
    ...DefaultPipelineSettings,
    pipelineType: PipelineType.CustomTest,
    cameraGain: 75,
    outputShowMultipleTargets: true,
    targetModel: TargetModel.AprilTag6p5in_36h11,
    cameraExposureRaw: -1,
    cameraAutoExposure: true,
    ledMode: false,

    featureThreshold: 1,
    minFeatures: 500,
    imageDifferenceThreshold: 150,
    essentialMatProb: 0.999,
    essentialMatThreshold: 1.,


    hammingDist: 0,
    numIterations: 40,
    decimate: 1,
    blur: 0,
    decisionMargin: 35,
    refineEdges: true,
    debug: false,
    threads: 4,
    tagFamily: AprilTagFamily.Family36h11,
    doMultiTarget: false,
    doSingleTargetAlways: false
};