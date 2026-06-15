# Project Plan for the Laundry Classification and Laundry Recommendation App

> This document is a project planning and development plan based on the initial project meeting and preliminary research results.

## 1. Project Overview

| Item | Description |
| --- | --- |
| Team | Team E |
| Project Topic | Laundry classification and laundry recommendation app |
| Core Technologies | YOLO-based laundry shape recognition, color extraction algorithm, user-supplemented input |
| Target Users | People living alone for the first time, users with limited laundry experience, and users unfamiliar with laundry sorting criteria |

This project aims to let users photograph laundry items, have the app analyze their basic shape and color, and then receive recommendations for washable groups after adding any necessary supplemental information.

Rather than trying to automatically determine all laundry information from a single photo, the project combines an image recognition model, post-processing algorithms, and a user confirmation step to design a practical laundry management flow.

## 2. Background for Topic Selection

During the initial meeting, several ideas were proposed, including classroom behavior recognition, subway congestion detection, a sign language recognition keyboard, fall detection, package detection, and street congestion recognition. The final topic selected from these ideas was a laundry classification and laundry recommendation app.

The reasons for selecting this topic are as follows.

- People living alone for the first time or users with limited laundry experience often have difficulty sorting laundry by clothing type, material, and color.
- Incorrect laundry sorting can cause clothing damage or color bleeding.
- Instead of requiring users to judge laundry criteria every time, the app can accumulate laundry information and recommend appropriate washing timing and combinations.
- The topic is suitable for practice with image recognition models, and YOLO fine-tuning can be set as a major technical objective.

## 3. Project Goals

The final goal of this project is to develop a mobile app that recommends washable groups based on the basic shape and color information of laundry items.

To achieve this, the following goals are defined.

- Use a YOLO model to perform first-stage classification of basic laundry shapes.
- Extract the main color based on the detected laundry region.
- Provide an input process where users can review and correct automatic recognition results.
- Automatically classify washable groups based on registered laundry information.
- Design a flow that notifies the user of a suitable washing time when enough laundry has accumulated.

## 4. Preliminary Research Results

### 4.1 Initial Concept

The initial concept was a system that could automatically recognize all of the following information from a single photo.

- Clothing type
- Clothing material
- Clothing color
- Whether the item can be washed
- Combinations that can be washed together

However, preliminary research showed that it is not realistic to accurately determine all of this information using only a single YOLO model. In particular, material and washability are difficult to judge reliably from photos alone, and color can also vary depending on lighting, shadows, and camera quality.

### 4.2 Limitations of Applying YOLO

Using a YOLO model as-is has the following limitations.

- The default YOLO model does not include enough classes to distinguish detailed laundry types or materials.
- When clothes are spread on the floor or wrinkled, their shapes can differ from those in general clothing datasets, which may reduce recognition accuracy.
- It is difficult to accurately determine material from photos alone.
- Color is heavily affected by the shooting environment, so it is not suitable to handle it with YOLO alone.

Therefore, YOLO should be used not as a model that determines all information, but as a first-stage classification model that recognizes the basic shape of laundry items.

## 5. Technical Direction

### 5.1 YOLO-Based Shape Recognition

YOLO focuses on classifying the approximate shape of laundry items. Rather than designing YOLO to fully determine detailed clothing types or materials, it is used as a basic shape recognition step that can support user input.

Expected classification targets are as follows.

- Top
- Bottom
- Towel
- Socks
- Other laundry items

### 5.2 Color Extraction Algorithm

Color information is extracted not by the YOLO model, but by a separate post-processing algorithm. The main color is analyzed based on the laundry region detected by YOLO and then organized into color groups that can be used for laundry classification.

Expected color classification directions are as follows.

- White color group
- Dark color group
- Colored group

### 5.3 User-Supplemented Input

Because automatic recognition results cannot always be assumed to be accurate, the app includes a process where users can review and correct the results.

Examples are as follows.

- If YOLO recognizes an item as a bottom, the user selects a more detailed type such as jeans, cotton pants, or slacks.
- If the color extraction result is inaccurate, the user corrects the color group.
- If material judgment is needed, the user manually enters the material information.

This structure compensates for the limitations of the model and creates a practical app flow.

## 6. App Operation Process

The overall operation process of the app is as follows.

```text
Place laundry items
    v
Take a camera photo
    v
Recognize laundry shape with YOLO
    v
Apply color extraction algorithm
    v
User review and detailed information input
    v
Register in DB
    v
Automatically classify washable groups
    v
Send washing time notification
```

The detailed process is as follows.

1. The user spreads the clothes to be washed on the floor.
2. The user photographs the laundry items with the app camera.
3. The YOLO model recognizes the basic shape of each laundry item.
4. A separate algorithm extracts color information from the detected region.
5. The user reviews the recognition results and corrects any necessary information.
6. Laundry information is stored in the DB.
7. The app automatically groups items that can be washed together.
8. When enough laundry has accumulated, the app notifies the user of a suitable washing time.
9. Items that have been washed are excluded from the record.

## 7. Dataset and Training Plan

For initial training, the DeepFashion2 Dataset will be used to evaluate the feasibility of clothing shape classification.

However, the data required for this project differs from general fashion images. In real usage, clothes are likely to be placed on the floor or wrinkled. Therefore, existing datasets alone are not enough to fully reflect real usage scenarios, and additional data suited to the project goal is required.

The following additional data should be secured.

- Images of tops spread on the floor
- Images of bottoms spread on the floor
- Towel images
- Socks images
- Images of wrinkled laundry items
- Test images photographed directly by team members

Classes with insufficient data will be supplemented through additional data collection by team members and used for further training.

## 8. Risks and Mitigation Plans

The largest current risk is that the YOLO model may not sufficiently distinguish clothing shapes in real laundry photos. In particular, recognition performance may be low for clothes placed on the floor, wrinkled clothes, and targets such as towels and socks that are not sufficiently covered in general clothing datasets.

Mitigation plans are as follows.

- Quickly train a first-stage model using existing datasets.
- Perform real-world testing with directly photographed images.
- Distinguish between classes that can be recognized and classes that are difficult to recognize.
- Add direct datasets for insufficient classes such as towels and socks.
- If implementing the core feature is judged to be difficult, quickly switch to another project topic.

## 9. Expected Effects

If this project is implemented, users will be able to organize basic laundry information through the app without sorting each laundry item manually. They will also be able to reduce the risk of clothing damage and color bleeding by receiving recommendations for washable combinations.

From a project perspective, the team can gain experience applying an image recognition model to a real service structure by handling YOLO fine-tuning, image post-processing, user-supplemented input, and DB-based group classification together.

## 10. Conclusion

The laundry classification and laundry recommendation app is a project that combines YOLO-based image recognition with user-supplemented input to reduce the inconvenience of laundry sorting.

Preliminary research showed that it is difficult to perfectly and automatically determine clothing type, material, and color from a single photo. Therefore, YOLO is designed to focus on basic laundry shape recognition, color extraction is handled by a separate algorithm, and detailed information is supplemented by the user.

Going forward, the project will verify feasibility through YOLO execution environment setup, first-stage training based on DeepFashion2, and testing with directly photographed images.
