# Top/Bottom Detail Classification Model Performance Analysis

## 1. Analysis Target

The detail classification models are classification models that classify the specific type of a top or bottom after cropping the laundry region detected by the main detection model. In the app, a crop image is passed to a detail classification model only when the main detection result is `top` or `bottom`.

| Item | Top detail classification model | Bottom detail classification model |
| --- | --- | --- |
| Base model | `yolo26n-cls.pt` | `yolo26n-cls.pt` |
| Task type | Classification | Classification |
| Training epoch | 50 | 50 |
| Input image size | 224 | 224 |
| batch | 64 | 64 |
| Evaluation checkpoint | `best.pt` | `best.pt` |
| Evaluation split | `val` | `val` |

The top model classifies six classes: `Activewear`, `Denim`, `Hoodies`, `Shirts`, `Sweaters`, and `T-shirts`. The bottom model classifies six classes: `Activewear`, `Chinos`, `Jeans`, `Joggers`, `Skirts`, and `Slacks`.

## 2. Dataset Composition

Both models were built in the Ultralytics classification training structure after applying CLIP-based class selection to images cropped from bbox annotations in the original DeepFashion2 data. The train/validation split ratio is 85:15.

| Model | train images | val images | total images |
| --- | ---: | ---: | ---: |
| Top detail classification | 14,450 | 2,550 | 17,000 |
| Bottom detail classification | 15,300 | 2,700 | 18,000 |

Most classes in the top dataset were aligned to about 3,000 images, but `Hoodies` has 2,000 images, fewer than the other top classes.

| Top class | train |  val | total |
| ---------- | ----: | ---: | ----: |
| Activewear | 2,550 |  450 | 3,000 |
| Denim      | 2,550 |  450 | 3,000 |
| Hoodies    | 1,700 |  300 | 2,000 |
| Shirts     | 2,550 |  450 | 3,000 |
| Sweaters   | 2,550 |  450 | 3,000 |
| T-shirts   | 2,550 |  450 | 3,000 |

The bottom dataset is balanced, with all six classes containing 3,000 images each.

| Bottom class | train |  val | total |
| ---------- | ----: | ---: | ----: |
| Activewear | 2,550 |  450 | 3,000 |
| Chinos     | 2,550 |  450 | 3,000 |
| Jeans      | 2,550 |  450 | 3,000 |
| Joggers    | 2,550 |  450 | 3,000 |
| Skirts     | 2,550 |  450 | 3,000 |
| Slacks     | 2,550 |  450 | 3,000 |

## 3. Overall Performance Comparison

Both models measured top5 accuracy of 1.0000, so the correct class is almost always included among the top candidates. However, because the app ultimately stores a single detail type, top1 accuracy and class-level confusion should be reviewed together.

| Model | top1 accuracy | top5 accuracy | macro precision | macro recall | macro F1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Top detail classification | 0.9549 | 1.0000 | 0.9503 | 0.9520 | 0.9510 |
| Bottom detail classification | 0.9215 | 1.0000 | 0.9210 | 0.9215 | 0.9211 |

The top model shows very stable performance, with top1 95.49% and macro F1 95.10%. The bottom model is also usable, with top1 92.15% and macro F1 92.11%, but it is lower than the top model. This can be interpreted as a result of bottom classes often looking similar to each other in crop images.

## 4. Training Curve Interpretation

### 4.1 Top Model

<p align="center">
  <img src="assets/detail-top-training-curves.png" alt="Top detail classification model training curves" width="720">
</p>

For the top model, train loss and val loss decreased stably over 50 epochs, and top1 accuracy converged at a high level.

|              epoch | train loss | val loss |   top1 |   top5 |
| -----------------: | ---------: | -------: | -----: | -----: |
|                  1 |     0.9906 |   0.3779 | 0.8753 | 0.9984 |
|                 25 |     0.1491 |   0.2129 | 0.9357 | 0.9992 |
|                 50 |     0.0391 |   0.1991 | 0.9522 | 1.0000 |
|     best top1 (45) |     0.0446 |   0.1900 | 0.9549 | 1.0000 |
| best val loss (37) |     0.0845 |   0.1796 | 0.9522 | 0.9996 |

The best top1 was recorded at epoch 45 with 0.9549. The lowest val loss was recorded at epoch 37, and top1 remained at a similar level afterward. While train loss continued to decrease in the later phase, val loss did not collapse sharply, so the current checkpoint can be considered a stable deployment candidate.

### 4.2 Bottom Model

<p align="center">
  <img src="assets/detail-bottom-training-curves.png" alt="Bottom detail classification model training curves" width="720">
</p>

The bottom model recorded both best top1 and best val loss at epoch 37. When training continued to epoch 50, train loss kept decreasing, but val loss increased and top1 declined slightly.

|              epoch | train loss | val loss |   top1 |   top5 |
| -----------------: | ---------: | -------: | -----: | -----: |
|                  1 |     1.0479 |   0.4618 | 0.8307 | 0.9996 |
|                 25 |     0.2110 |   0.2737 | 0.9133 | 1.0000 |
|                 50 |     0.0536 |   0.2978 | 0.9182 | 1.0000 |
|     best top1 (37) |     0.1238 |   0.2541 | 0.9226 | 1.0000 |
| best val loss (37) |     0.1238 |   0.2541 | 0.9226 | 1.0000 |

The bottom model has more ambiguous boundaries between classes than the top model, and weak overfitting signals appear after epoch 37. The final validation report was reevaluated based on `best.pt`, so it maintains a top1 level of 0.9215.

## 5. Top Detail Classification Performance

<p align="center">
  <img src="assets/detail-top-class-metrics.png" alt="Top detail classification model class-level performance" width="720">
</p>

The top model recorded F1 0.92 or higher for most classes. `T-shirts`, `Shirts`, `Sweaters`, and `Denim` are highly stable, while `Hoodies` is relatively the weakest class.

| class      | support | precision | recall |     F1 |
| ---------- | ------: | --------: | -----: | -----: |
| Activewear |     450 |    0.9488 | 0.9067 | 0.9273 |
| Denim      |     450 |    0.9690 | 0.9733 | 0.9712 |
| Hoodies    |     300 |    0.8658 | 0.9033 | 0.8842 |
| Shirts     |     450 |    0.9712 | 0.9756 | 0.9734 |
| Sweaters   |     450 |    0.9754 | 0.9711 | 0.9733 |
| T-shirts   |     450 |    0.9714 | 0.9822 | 0.9768 |

<p align="center">
  <img src="assets/detail-top-confusion-matrix-normalized.png" alt="Top detail classification model normalized confusion matrix" width="640">
</p>

The largest confusion in the top model occurred between `Activewear` and `Hoodies`.

| actual     | predicted  | count |
| ---------- | ---------- | ----: |
| Activewear | Hoodies    |    22 |
| Hoodies    | Activewear |    13 |
| Activewear | T-shirts   |    11 |
| Hoodies    | Denim      |     7 |
| Sweaters   | Hoodies    |     7 |
| Hoodies    | Shirts     |     5 |
| Shirts     | Hoodies    |     5 |
| Activewear | Sweaters   |     4 |

`Hoodies` also has only 300 support images, fewer than the other major classes, and when the hood is not clearly visible or the crop looks like sportswear, it can be confused with `Activewear`. In the app, it is advisable to keep the confirmation flow so users can easily correct predictions when `Hoodies` confidence is low.

## 6. Bottom Detail Classification Performance

<p align="center">
  <img src="assets/detail-bottom-class-metrics.png" alt="Bottom detail classification model class-level performance" width="720">
</p>

In the bottom model, `Jeans` and `Skirts` are very strong, while `Joggers`, `Chinos`, and `Activewear` are relatively weak. Pants classes in particular are confused more often because silhouette and material cues can look similar in crop images.

| class      | support | precision | recall |     F1 |
| ---------- | ------: | --------: | -----: | -----: |
| Activewear |     450 |    0.8957 | 0.8778 | 0.8866 |
| Chinos     |     450 |    0.8803 | 0.8822 | 0.8812 |
| Jeans      |     450 |    0.9802 | 0.9889 | 0.9845 |
| Joggers    |     450 |    0.8881 | 0.8467 | 0.8669 |
| Skirts     |     450 |    0.9737 | 0.9889 | 0.9813 |
| Slacks     |     450 |    0.9081 | 0.9444 | 0.9259 |

<p align="center">
  <img src="assets/detail-bottom-confusion-matrix-normalized.png" alt="Bottom detail classification model normalized confusion matrix" width="640">
</p>

The main confusion in the bottom model is concentrated among `Joggers`, `Slacks`, `Chinos`, and `Activewear`.

| actual     | predicted  | count |
| ---------- | ---------- | ----: |
| Joggers    | Slacks     |    27 |
| Activewear | Chinos     |    24 |
| Chinos     | Activewear |    21 |
| Joggers    | Activewear |    20 |
| Activewear | Joggers    |    18 |
| Joggers    | Chinos     |    17 |
| Chinos     | Joggers    |    16 |
| Slacks     | Joggers    |    13 |

`Jeans` and `Skirts` show high performance because their shape and material cues are relatively distinct. In contrast, `Joggers`, `Chinos`, `Slacks`, and `Activewear` have ambiguous boundaries within pants-type crops, so reviewing hard examples is likely to be the most effective next improvement.

## 7. App Application Perspective

Both detail classification models are sufficient for use as automatic input assistance features in the app. The top model is stable at over 95% top1, and the bottom model also provides the correct detail type candidate in most cases at about 92% top1.

However, it is safer to treat detail classification results as values that users confirm before final storage. In particular, the confirmation UI should assume the possibility of correction in the following cases.

- A top is predicted as `Hoodies` or `Activewear`, but confidence is low
- A bottom is predicted as one of `Joggers`, `Chinos`, `Slacks`, or `Activewear`
- The crop image does not sufficiently include the whole garment, or folds/shadows make the silhouette unclear

In the next improvement cycle, prioritizing the bottom model over the top model would be more efficient. In the bottom model, reducing confusion around `Joggers` and `Chinos` connects most directly to improving perceived performance.

## 8. Conclusion

The top detail classification model shows very stable performance, with top1 0.9549 and macro F1 0.9510. The remaining weakness is `Hoodies`, which is mainly confused with `Activewear`.

The bottom detail classification model is usable, with top1 0.9215 and macro F1 0.9211, but it is lower than the top model. `Jeans` and `Skirts` are strong, while confusion among `Joggers`, `Chinos`, `Activewear`, and `Slacks` is the main target for improvement.

Therefore, the current models are usable detail classification candidates for the app, and follow-up improvements should focus on hard cases around bottom pants classes.
