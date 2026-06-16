# Main Detection Model Performance Analysis

## 1. Analysis Target

The main detection model is a detector that first finds laundry in the app and classifies detected objects into four top-level categories: `top`, `bottom`, `socks`, and `towel`. Since tops and bottoms are then passed to separate detail classification models, the key role of this model is to reliably find laundry regions and pass them to the app's basic category flow.

| Item | Description |
| --- | --- |
| Base model | `yolo26n.pt` |
| Task type | Object detection |
| Output classes | `top`, `bottom`, `socks`, `towel` |
| Training epoch | 100 |
| Input image size | 640 |
| Evaluation checkpoint | `best.pt` |
| Final evaluation split | `test` split not used for training |

## 2. Dataset Composition

This training dataset was built around the four main labels directly used by the app. Tops and bottoms use the original images and bbox labels from crop provenance selected with CLIP, while socks and towels were added from separate source datasets.

The full dataset contains 38,253 images and 46,851 objects.

| split | images | objects |
| ----- | -----: | ------: |
| train | 30,600 |  37,417 |
| val   |  3,823 |   4,583 |
| test  |  3,830 |   4,851 |
| total | 38,253 |  46,851 |

By class object count, `top` and `bottom` are large enough, and `socks` also has a relatively high object count. In contrast, `towel` has the smallest share of the dataset, so it is the class most likely to show unstable model performance.

| split |    top | bottom | socks | towel |  total |
| ----- | -----: | -----: | ----: | ----: | -----: |
| train | 13,601 | 14,405 | 6,877 | 2,534 | 37,417 |
| val   |  1,697 |  1,797 |   776 |   313 |  4,583 |
| test  |  1,702 |  1,798 | 1,011 |   340 |  4,851 |

## 3. Training Curve Interpretation

<p align="center">
  <img src="assets/main-model-training-curves.png" alt="YOLO26n main detection model 100 epoch training curves" width="720">
</p>

Over 100 epochs, box loss and classification loss generally decreased in a stable pattern. Since validation mAP continued to rise gradually until the final epoch, the 100 epoch run can be interpreted as a phase that actually improved performance rather than excessive repetition.

| epoch | precision | recall | mAP50 | mAP50-95 | train box loss | val box loss | train cls loss | val cls loss |
| ----: | --------: | -----: | ----: | -------: | -------------: | -----------: | -------------: | -----------: |
|     1 |     0.317 |  0.296 | 0.225 |    0.101 |          1.474 |        1.931 |          2.286 |        3.471 |
|    50 |     0.784 |  0.729 | 0.763 |    0.612 |          0.907 |        0.790 |          1.042 |        0.894 |
|   100 |     0.781 |  0.794 | 0.806 |    0.654 |          0.693 |        0.748 |          0.681 |        0.841 |

The main observations are as follows.

- The best mAP50-95 performance was recorded at epoch 100.
- mAP50 and mAP50-95 continued to improve after epoch 50, showing that additional training was effective.
- Precision was similar between epochs 50 and 100, but recall improved from 0.729 to 0.794.
- There was no sharp collapse in validation performance in the later phase.

## 4. Final Test Performance

Final performance was evaluated again on the `test` split, which was not used for training or validation. Overall, the model recorded precision 0.813, recall 0.745, mAP50 0.789, and mAP50-95 0.635.

| class  | images | objects | precision | recall | mAP50 | mAP50-95 |
| ------ | -----: | ------: | --------: | -----: | ----: | -------: |
| all    |  3,830 |   4,851 |     0.813 |  0.745 | 0.789 |    0.635 |
| top    |  1,694 |   1,702 |     0.726 |  0.771 | 0.762 |    0.645 |
| bottom |  1,795 |   1,798 |     0.743 |  0.790 | 0.795 |    0.676 |
| socks  |    158 |   1,011 |     0.942 |  0.908 | 0.960 |    0.776 |
| towel  |    183 |     340 |     0.841 |  0.512 | 0.640 |    0.444 |

<p align="center">
  <img src="assets/main-model-test-class-metrics.png" alt="Main detection model class-level test performance" width="720">
</p>

## 5. Class-Level Interpretation

`socks` is the most stable class. It showed the highest performance among the four classes, with precision 0.942, recall 0.908, and mAP50 0.960. Socks have many objects and relatively distinct shape features, so the model appears to have learned them well.

`bottom` is a well-balanced class. With precision 0.743, recall 0.790, and mAP50-95 0.676, it shows the most practical performance among the classes from an app usage perspective.

`top` is similar to `bottom`, but its precision is slightly lower. In the actual app, it is likely to find tops reasonably well, but it may incorrectly detect some other objects as tops slightly more often than bottoms.

`towel` is the biggest target for improvement. Precision 0.841 is not bad, but recall is only 0.512, so even though detected towels are relatively accurate, the model often misses real towels. This is likely because towel data is smaller than the other classes and because folded or spread towel shapes vary widely, making the class harder to learn.

## 6. App Application Perspective

This 100 epoch model can be treated as a deployment candidate for the detector that handles the app's four main categories. Overall mAP50 is 0.789, and `top`, `bottom`, and `socks` show performance levels usable for the app's first-stage classification flow.

However, `towel` needs separate handling. The current towel class has a larger recall problem than precision problem, so if missed towels matter for the user experience, the following improvements should be considered first.

- Collect and quality-check additional towel data
- Add hard cases such as folded towels, spread towels, and towels with colors similar to the background
- Adjust the confidence threshold separately for the towel class
- Check in the app flow whether towel false negatives are a bigger problem than false positives

## 7. Conclusion

The `yolo26n_clip_original_yolo_towel3187` model is a 100 epoch detector trained for the existing four-class main category system, and it can be summarized as an app application candidate in terms of overall performance and training stability. In particular, `socks`, `bottom`, and `top` show relatively stable performance even with the current dataset composition.

The key follow-up improvement is `towel`. Towels have sufficient precision, but low recall means missed detections are likely. In the next training cycle, it would be most efficient to prioritize towel data reinforcement, hard-negative review, and threshold tuning rather than further increasing top and bottom data.
