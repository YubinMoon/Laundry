# Laundry Classification Service

A service that helps separate and wash laundry by storing and identifying it through photos.

## Core Logic

### Laundry Registration

1. User takes a photo of the laundry with a camera.
2. The captured image is classified by type using YOLO.
3. Color is classified through an algorithm.
4. Material is classified through user input.

### Laundry Classification

1. Create groups for each combination of type, color, and material.
2. Add laundry to each group.

### Laundry Recommendation

1. Check the number of laundry items in each group.
2. Recommend laundry to the user when it exceeds a threshold.
3. Reset the group after washing is complete.

## Development Sequence

### Define labels to be classified via YOLO

- Tops [T-shirts, Shirts, Sweaters, Hoodies, Activewear, Denim]
- Bottoms [Chinos, Slacks, Joggers, Activewear, Skirts, Jeans]
- Towels
- Socks

### Prepare dataset for YOLO training

Classify Tops and Bottoms classes based on FashionDataset2 - method described below.
Extract Towels and Socks data from open source datasets.

#### How to prepare Tops and Bottoms datasets

Prepared dataset

[Link](https://drive.google.com/file/d/16-2kHHYwhXMtn_zckTDlaIMrPsIAkIAd/view?usp=drive_link)

```
dataset/[0-9]+/(images|labels)/[0-9].(jpg|txt)
```

In the dataset above, the `[0-9]+` part is the Category_id of the existing FashionDataset2 and has the following values:

1. short_sleeved_shirt
2. long_sleeved_shirt
3. short_sleeved_outwear
4. long_sleeved_outwear
5. vest 
6. sling 
7. shorts 
8. trousers 
9. skirt 
10. short_sleeved_dress 
11. long_sleeved_dress 
12. vest_dress 
13. sling_dress 

Perform classification with reference to this.

- Create a `new_dataset` directory.
- Create class directories (Skirts, Jeans, Etc...) for later classification.
- Store more than 1500 pieces of data in each directory.
- Compress all data and share via Drive.

### YOLO Training

Planned for later.

### App Development

Planned for later.
