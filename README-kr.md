# 세탁물 분류 서비스

사진을 통해 세탁물을 저장하고 구분해 분리 세탁을 도와주는 서비스

## 핵심 로직

### 세탁물 등록

1. 유저가 카메라로 세탁물 촬영
2. 촬영된 이미지를 YOLO를 통해 종류 구분
3. 알고리즘을 통해 색상 구분
4. 유저 입력으로 재질 구분

### 세탁물 분류

1. 종류, 색상, 재질 조합 별 그룹 생성
2. 각 그룹에 세탁물 추가

### 세탁 추천

1. 각 그룹 별 세탁물 수 확인
2. 임계값 이상일 시 유저에게 세탁을 추천
3. 세탁 완료한 그룹 초기화

## 개발 순서

### YOLO를 통해 분류할 라벨 정의

- Tops[T-shirts, Shirts, Sweaters, Hoodies, Activewear, Denim]
- Bottoms[Chinos, Slacks, Joggers, Activewear, Skirts, Jeans]
- Towels
- Socks

### YOLO 학습용 데이터셋 준비

FashionDataset2 기반으로 Tops, Bottoms의 class 분류 - 방법은 후술
오픈소스 Dataset에서 Towels와 Socks 데이터 추출

#### Tops, Bottoms 데이터셋 준비 방법

준비된 데이터셋

[Link](https://drive.google.com/file/d/16-2kHHYwhXMtn_zckTDlaIMrPsIAkIAd/view?usp=drive_link)

```
dataset/[0-9]+/(images|labels)/[0-9].(jpg|txt)
```

위 데이터 셋에서 `[0-9]+` 부분은 기존 FashionDataset2의 Categori_id이며 아래와 같은 값을 가진다.

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

이를 참고하여 분류를 진행한다.

- `new_dataset` 디렉터리를 생성
- 후 구분을 위한 class 디렉터리(Skirts, Jeans, Etc...)를 생성
- 각 디렉터리에 1500개 이상의 데이터를 저장
- 전체 데이터를 압축 후 drive로 공유

### YOLO 학습

추후 계획 예정

### 앱 개발

추후 계획 예정

