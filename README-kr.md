# Laundry Mate

<p align="center">
  <a href="README-kr.md"><b>한국어</b></a>
  ·
  <a href="README.md">English</a>
</p>

사진으로 세탁물을 인식하고, 색상과 종류에 따라 세탁 그룹을 만들어 분리 세탁을 돕는 Android 앱입니다. 카메라 화면에서 세탁물을 감지하면 사용자는 인식 결과를 확인하고 저장할 수 있으며, 저장된 세탁물은 그룹별 추천 순서와 세탁 팁으로 이어집니다.

## 앱 화면

<!-- TODO: 아래 경로에 실제 앱 화면 4장을 추가한 뒤 이 주석을 삭제하세요. 권장 파일명: docs/assets/screen-home.png, docs/assets/screen-register.png, docs/assets/screen-groups.png, docs/assets/screen-tips.png -->

<table>
  <tr>
    <td><img src="docs/assets/screen-home.png" alt="홈 화면" width="180"></td>
    <td><img src="docs/assets/screen-register.png" alt="세탁물 등록 화면" width="180"></td>
    <td><img src="docs/assets/screen-groups.png" alt="세탁 그룹 화면" width="180"></td>
    <td><img src="docs/assets/screen-tips.png" alt="세탁 팁 화면" width="180"></td>
  </tr>
  <tr>
    <td align="center">홈</td>
    <td align="center">세탁물 등록</td>
    <td align="center">세탁 그룹</td>
    <td align="center">세탁 팁</td>
  </tr>
</table>

## 빨래 인식 플로우

<!-- TODO: 빨래 인식 후 결과 확인 모달이 생성되는 GIF를 docs/assets/detection-modal.gif 경로에 추가한 뒤 이 주석을 삭제하세요. -->

<p align="center">
  <img src="docs/assets/detection-modal.gif" alt="빨래 인식 후 결과 확인 모달" width="200">
</p>

1. 사용자가 등록 화면에서 카메라 프리뷰를 시작합니다.
2. 대분류 감지 모델이 프레임 안의 세탁물을 감지하고 `top`, `bottom`, `socks`, `towel` 라벨을 앱의 `상의`, `하의`, `양말`, `수건` 범주로 매핑합니다.
3. 감지된 영역을 crop한 뒤, 상의/하의에 해당하는 경우 소분류 모델로 세부 종류를 분류합니다.
4. 색상 분석 결과와 모델 분류 결과를 함께 보여주는 확인 모달을 생성합니다.
5. 사용자가 저장하면 세탁물 기록과 crop 이미지가 로컬 DB에 저장되고, 세탁 그룹에 자동 배정됩니다.

## 학습 모델

앱에는 `YOLO26n` 기반 대분류 감지 모델 1개와 `YOLO26n-cls` 기반 소분류 모델 2개가 포함되어 있습니다.

대분류 모델로 `YOLO26m`, `YOLO26s`, `YOLO26n` 모델을 모두 테스트 하였지만 모델 간 차이가 크지 않아 학습이 비교적 빠른 `YOLO26n` 모델로 많은 epoch의 학습을 통해 성능을 끌어올렸다.

| 모델                             | 역할                                      | 출력 클래스                                                        | 학습 데이터셋 크기              |
| -------------------------------- | ----------------------------------------- | ------------------------------------------------------------------ | ------------------------------- |
| 대분류 모델 (`YOLO26n`)          | 세탁물 영역 감지 및 앱 상위 카테고리 분류 | `top`, `bottom`, `socks`, `towel`                                  | 상의/하의 18k, 양말 8k, 수건 3k |
| 상의 소분류 모델 (`YOLO26n-cls`) | 상의 crop 이미지 세부 분류                | `Activewear`, `Denim`, `Hoodies`, `Shirts`, `Sweaters`, `T-shirts` | 각 타입 3k                      |
| 하의 소분류 모델 (`YOLO26n-cls`) | 하의 crop 이미지 세부 분류                | `Activewear`, `Chinos`, `Jeans`, `Joggers`, `Skirts`, `Slacks`     | 각 타입 3k                      |

## 모델 평가 결과

### 상의/하의 소분류 모델

| 모델 | val accuracy/top1 | top5 | macro F1 | weighted F1 |
| ---- | ----------------- | ---- | -------- | ----------- |
| 상의 | 95.49%            | 100% | 95.10%   | 95.49%      |
| 하의 | 92.15%            | 100% | 92.11%   | 92.11%      |

### 대분류 모델

| class  | images | objects | precision | recall | mAP50 | mAP50-95 |
| ------ | -----: | ------: | --------: | -----: | ----: | -------: |
| all    |  3,830 |   4,851 |     0.813 |  0.745 | 0.789 |    0.635 |
| top    |  1,694 |   1,702 |     0.726 |  0.771 | 0.762 |    0.645 |
| bottom |  1,795 |   1,798 |     0.743 |  0.790 | 0.795 |    0.676 |
| socks  |    158 |   1,011 |     0.942 |  0.908 | 0.960 |    0.776 |
| towel  |    183 |     340 |     0.841 |  0.512 | 0.640 |    0.444 |

towel 클래스는 다른 클래스보다 recall이 낮아, 수건 누락을 줄이는 것이 주요 개선 대상입니다.

## 데이터셋 출처

| 데이터 범위           | 출처                                                                                     | 사용 목적                                        |
| --------------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------ |
| 상의/하의 원천 이미지 | [DeepFashion2 Dataset](https://github.com/switchablenorms/DeepFashion2)                  | bbox와 label이 포함된 상의/하의 학습 데이터 구성 |
| 수건/양말 원천 이미지 | [Roboflow Universe](https://universe.roboflow.com/)                                      | 대분류 모델의 `towel`, `socks` 클래스 구성       |
| 수건 보강 이미지      | [Open Images Dataset V7](https://storage.googleapis.com/openimages/web/download_v7.html) | 부족한 `towel` 오브젝트 보강                     |

## 데이터셋 준비 방법

RAW 데이터셋에서 앱에 필요한 클래스 이미지만 추출해 모델 학습용 데이터셋을 새로 구성했습니다. 최종 데이터셋은 대분류 감지 모델용 데이터셋과 상의/하의 소분류 모델용 데이터셋으로 나누어 관리합니다. 상의/하의 소분류 모델을 먼저 구성한 뒤, 이 과정에서 품질이 확인된 crop 이미지의 원본 이미지와 bbox label을 대분류 모델 학습 데이터로 재사용했습니다.

### RAW 데이터셋 선정

기본 의류 데이터는 DeepFashion2 Dataset을 채택했습니다. DeepFashion2는 쇼핑몰 의류 이미지뿐 아니라 일반 사용자가 직접 촬영한 이미지도 포함하고 있어, 다양한 상태의 옷을 구분해야 하는 프로젝트 목적에 적합하다고 판단했습니다. 또한 801k 규모의 큰 데이터셋에 bbox와 label이 제공되어 학습용 데이터셋으로 가공하기에 적합했습니다.

수건과 양말 데이터셋은 주로 Roboflow Universe의 공개 데이터셋을 활용했습니다. 양말 데이터는 비교적 충분한 양을 확보할 수 있었지만, 수건 데이터는 Roboflow Universe만으로 약 2.5k 오브젝트 수준이었기 때문에 프로젝트 후반에 Open Images Dataset V7에서 수건 데이터를 추가로 추출해 최종 3k 규모로 구성했습니다.

### 대분류 모델 데이터셋

대분류 모델은 `YOLO26n`을 사용해 세탁물을 화면에서 찾고, 감지된 물체를 앱의 상위 카테고리로 바로 매핑하기 위해 `top`, `bottom`, `socks`, `towel` 4개 라벨로 학습했습니다. `top`과 `bottom` 데이터는 CLIP으로 세부 타입 선별을 통과한 crop 이미지의 원본 이미지와 해당 bbox label에서 구성했고, 여기에 Roboflow Universe에서 확보한 양말/수건 데이터와 Open Images Dataset V7에서 추가 추출한 수건 데이터를 더했습니다.

| 앱 카테고리 | 모델 라벨 | 데이터셋 크기 | 준비 방식                                                         |
| ----------- | --------- | ------------- | ----------------------------------------------------------------- |
| 상의        | `top`     | 18k           | CLIP 선별을 통과한 상의 crop의 원본 이미지를 `top`으로 통합       |
| 하의        | `bottom`  | 18k           | CLIP 선별을 통과한 하의 crop의 원본 이미지를 `bottom`으로 통합    |
| 양말        | `socks`   | 8k            | Roboflow Universe에서 확보한 양말 오브젝트를 `socks`로 구성       |
| 수건        | `towel`   | 3k            | Roboflow Universe 수건 데이터에 Open Images V7 추출 데이터를 보강 |

### DeepFashion2 카테고리 분리

DeepFashion2의 기본 `category_id` 중 dress 계열은 제외하고, 상의와 하의에 해당하는 카테고리만 사용했습니다.

| 사용 범위 | DeepFashion2 category_id     | 원본 카테고리                                                                             |
| --------- | ---------------------------- | ----------------------------------------------------------------------------------------- |
| 상의      | `1`, `2`, `3`, `4`, `5`, `6` | short sleeve top, long sleeve top, short sleeve outwear, long sleeve outwear, vest, sling |
| 하의      | `7`, `8`, `9`                | shorts, trousers, skirt                                                                   |
| 제외      | `10`, `11`, `12`, `13`       | short sleeve dress, long sleeve dress, vest dress, sling dress                            |

### 상의/하의 소분류 데이터셋

상의와 하의 소분류 모델은 DeepFashion2 원본 데이터에 기록된 bbox를 crop한 이미지에 CLIP 기반 세부 클래스 선별을 적용해 `YOLO26n-cls` 학습 데이터셋으로 구성했습니다. 이때 사용한 CLIP 모델은 `openai/clip-vit-base-patch32`입니다. CLIP은 별도 데이터 출처가 아니라, crop 이미지를 세부 의류 클래스로 나누고 학습에 사용할 품질 좋은 이미지를 고르기 위한 분류 도구로 사용했습니다. 앱에서도 대분류 모델이 감지한 영역을 crop한 뒤 해당 crop 이미지를 소분류 모델 입력으로 사용합니다.

1. DeepFashion2 원본 이미지와 annotation을 불러옵니다.
2. `category_id`가 `1`-`6`이면 상의, `7`-`9`이면 하의로 분리하고 `10`-`13` dress 계열은 제외합니다.
3. annotation의 bbox 좌표를 기준으로 의류 영역을 crop하고 crop 이미지를 별도로 저장합니다.
4. 목표 세부 클래스별 텍스트 프롬프트를 정의하고, `openai/clip-vit-base-patch32`로 crop 이미지와 프롬프트 간 유사도를 계산합니다.
5. 상의 세부 클래스(`Activewear`, `Denim`, `Hoodies`, `Shirts`, `Sweaters`, `T-shirts`)와 하의 세부 클래스(`Activewear`, `Chinos`, `Jeans`, `Joggers`, `Skirts`, `Slacks`)별 점수를 매깁니다.
6. 각 세부 타입에서 CLIP 점수 상위 3,000장의 crop 이미지를 선택합니다.
7. 선택한 crop 이미지를 클래스 디렉터리에 저장하고 학습/검증/테스트 split을 구성합니다.
8. 선택된 crop 이미지의 원본 이미지와 bbox label은 `top`/`bottom` 대분류 학습 데이터로 다시 모읍니다.

## 색상 타입 분류 방법

색상 타입은 별도 학습 모델을 사용하지 않고, 대분류 모델이 안정적으로 감지한 세탁물 영역의 픽셀을 HSV 값으로 분석해 `White`, `Black`, `Light`, `Dark`, `Mixed` 중 하나로 분류합니다.

1. 감지된 bounding box를 원본 프레임 크기로 변환합니다.
2. 배경 영향이 섞이지 않도록 box 안쪽을 가로/세로 20%씩 줄인 영역을 샘플링합니다.
3. 샘플 영역에서 최대 64x64개 픽셀을 균등하게 뽑고, 각 픽셀의 RGB 값을 HSV로 변환합니다.
4. 픽셀별로 밝기(`V`)와 채도(`S`) 기준을 적용해 `White`, `Black`, `Light`, `Dark` 버킷에 넣습니다.
5. 밝은 계열(`White` + `Light`)이나 어두운 계열(`Black` + `Dark`)이 전체의 55% 이상이면 해당 계열에서 최종 타입을 고릅니다.
6. 어느 계열도 충분히 우세하지 않거나 분석할 수 없는 이미지이면 `Mixed`로 저장합니다.

| 색상 타입 | 분류 기준                                                   |
| --------- | ----------------------------------------------------------- |
| `White`   | 밝은 계열이 우세하고, 그중 `White` 픽셀 비율이 55% 이상     |
| `Black`   | 어두운 계열이 우세하고, 그중 `Black` 픽셀 비율이 35% 이상   |
| `Light`   | 밝은 계열이 우세하지만 `White` 비율이 기준보다 낮음         |
| `Dark`    | 어두운 계열이 우세하지만 `Black` 비율이 기준보다 낮음       |
| `Mixed`   | 밝은 계열과 어두운 계열 중 어느 쪽도 55% 이상 우세하지 않음 |

## 빨래 그룹 분류 방법

저장된 세탁물은 대분류, 소분류, 색상 결과를 기준으로 자동 그룹화됩니다. 이미 완료 처리된 그룹은 유지하고, 같은 이름의 미완료 그룹이 있으면 해당 그룹에 새 세탁물을 추가합니다.

| 그룹                    | 배정 조건                                                                | 세탁 방법                                               |
| ----------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------- |
| `Light General Clothes` | 색상이 `White` 또는 `Light`이고 다른 특수 그룹에 해당하지 않는 일반 의류 | <!-- TODO: 밝은색 일반 의류 세탁 방법을 입력하세요. --> |
| `Dark General Clothes`  | 색상이 `Black` 또는 `Dark`이고 다른 특수 그룹에 해당하지 않는 일반 의류  | 뒤집어서 세탁해 물빠짐을 줄입니다.                      |
| `Mixed General Clothes` | 색상이 `Mixed`이거나 밝은색/어두운색으로 분류되지 않는 일반 의류         | <!-- TODO: 혼합색 일반 의류 세탁 방법을 입력하세요. --> |
| `Activewear`            | 소분류가 `Activewear`인 상의 또는 하의                                   | <!-- TODO: 기능성 의류 세탁 방법을 입력하세요. -->      |
| `Delicates`             | 소분류가 `Sweaters` 또는 `Skirts`인 의류                                 | <!-- TODO: 섬세 의류 세탁 방법을 입력하세요. -->        |
| `Light Denim`           | 소분류가 `Denim` 또는 `Jeans`이고 색상이 `White` 또는 `Light`            | 찬물을 사용하고 건조기 사용 가능 여부를 확인합니다.     |
| `Dark Denim`            | 소분류가 `Denim` 또는 `Jeans`이고 색상이 `Black` 또는 `Dark`             | 찬물을 사용하고 건조기 사용 가능 여부를 확인합니다.     |
| `Towels`                | 대분류가 `Towels`인 세탁물                                               | 흡수력 유지를 위해 섬유유연제 사용을 피합니다.          |

양말은 별도 세탁 그룹으로 고정하지 않고, 현재 색상 기준 일반 의류 그룹에 배정됩니다.

## 앱 동작 요약

- 홈: 저장된 세탁물 수, 미완료 그룹 수, 완료 그룹 수를 요약하고 오늘 세탁하기 좋은 그룹을 추천합니다.
- 등록: 카메라로 세탁물을 인식하고 결과 확인 모달에서 카테고리, 세부 종류, 색상을 검토한 뒤 저장합니다.
- 그룹: 추천 그룹, 의류 종류, 색상 기준으로 저장된 세탁물을 확인합니다.
- 팁: 수건, 어두운 옷, 데님 등 주요 그룹별 세탁 팁을 제공합니다.
