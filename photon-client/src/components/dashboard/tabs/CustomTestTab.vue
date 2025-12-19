<script setup lang="ts">
import { PipelineType } from "@/types/PipelineTypes";
import PvSelect from "@/components/common/pv-select.vue";
import PvSlider from "@/components/common/pv-slider.vue";
import PvSwitch from "@/components/common/pv-switch.vue";
import { computed, getCurrentInstance } from "vue";
import { useStateStore } from "@/stores/StateStore";
import type { ActivePipelineSettings } from "@/types/PipelineTypes";
import { useCameraSettingsStore } from "@/stores/settings/CameraSettingsStore";
import {useDisplay} from "vuetify/framework";

// TODO fix pipeline typing in order to fix this, the store settings call should be able to infer that only valid pipeline type settings are exposed based on pre-checks for the entire config section
// Defer reference to store access method
const currentPipelineSettings = computed<ActivePipelineSettings>(
    () => useCameraSettingsStore().currentPipelineSettings
);

const { mdAndDown } = useDisplay();

const interactiveCols = computed(() =>
    mdAndDown.value && (!useStateStore().sidebarFolded || useCameraSettingsStore().isDriverMode) ? 8 : 7
);

</script>

<template>
  <div v-if="currentPipelineSettings.pipelineType === PipelineType.CustomTest">
    <pv-slider
        v-model="currentPipelineSettings.featureThreshold"
        class="pt-2"
        :slider-cols="interactiveCols"
        label="Feature Threshold"
        tooltip="The sharpness required in a feature for FAST to detect it"
        :min="0"
        :max="100"
        :step="1"
        @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ featureThreshold: value }, false)"
    />
    <pv-slider
        v-model="currentPipelineSettings.minFeatures"
        class="pt-2"
        :slider-cols="interactiveCols"
        label="Minimum Features"
        tooltip="Minimum amount of features for calculation of camera translation"
        :min="0"
        :max="1000"
        :step="1"
        @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ minFeatures: value }, false)"
    />
    <pv-slider
        v-model="currentPipelineSettings.imageDifferenceThreshold"
        class="pt-2"
        :slider-cols="interactiveCols"
        label="Image Difference Threshold"
        tooltip="Minimum amount of difference between points to update position"
        :min="0"
        :max="1000"
        :step="1"
        @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ imageDifferenceThreshold: value }, false)"
    />
    <pv-slider
        v-model="currentPipelineSettings.essentialMatProb"
        class="pt-2"
        :slider-cols="interactiveCols"
        label="Essential Matrix Prob"
        tooltip="'Prob' for cv2.findEssentialMat"
        :min="0"
        :max="1"
        :step="0.001"
        @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ essentialMatProb: value }, false)"
    />
    <pv-slider
        v-model="currentPipelineSettings.essentialMatThreshold"
        class="pt-2"
        :slider-cols="interactiveCols"
        label="Essential Matrix Threshold"
        tooltip="'Threshold' for cv2.findEssentialMat"
        :min="0"
        :max="1"
        :step="0.001"
        @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ essentialMatThreshold: value }, false)"
    />
  </div>
</template>